package com.denis.ws;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.graphics.OrthographicCamera;

import com.github.czyzby.websocket.WebSocket;
import com.github.czyzby.websocket.WebSockets;
import com.github.czyzby.websocket.WebSocketListener;

public class MainWebsockets extends ApplicationAdapter {

    private SpriteBatch batch;

    // SPRITESHEET
    private Texture sheet;
    private TextureRegion[][] frames;
    private Animation<TextureRegion> animDown, animUp, animLeft, animRight;
    private Animation<TextureRegion> currentAnimation;

    // CAMERA
    private OrthographicCamera camera;

    // BACKGROUND
    private Texture background;

    // PLAYER
    private float posX = 140;
    private float posY = 210;
    private float speed = 200;

    // ANIMATION
    private float stateTime = 0f;

    // DRAWING
    private ShapeRenderer shapeRenderer;

    // JOYSTICK (PREMIUM)
    private Vector2 joystickCenter = new Vector2(150, 150);
    private Vector2 joystickKnob = new Vector2(150, 150);
    private float joystickOuterRadius = 80f;
    private float joystickInnerRadius = 40f;
    private boolean joystickActive = false;
    private Vector2 moveVector = new Vector2(0, 0);

    // WEBSOCKET
    private WebSocket socket;
    private float timer = 0f;
    private boolean socketConnected = false;

    @Override
    public void create() {

        batch = new SpriteBatch();
        shapeRenderer = new ShapeRenderer();
        camera = new OrthographicCamera();
        camera.setToOrtho(false, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());

        // LOAD SPRITESHEET
        sheet = new Texture("dawn.png");

        // Evitar bordes borrosos y mezcla ("bleeding") entre frames vecinos si la
        // imagen es pixel art
        sheet.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);

        // ATENCIÓN: Te he cambiado las columnas de 5 a 4. Si ves dos trozos es 100% que
        // tu imagen
        // no tiene 5 columnas exactas, lo estándar de estos sprites es 4 (o 3).
        int COLS = 4;
        int ROWS = 4;

        frames = TextureRegion.split(
                sheet,
                sheet.getWidth() / COLS,
                sheet.getHeight() / ROWS);

        // Configuramos las animaciones (Orden estándar RPG: Abajo, Izquierda, Derecha,
        // Arriba)
        animDown = new Animation<>(0.15f, frames[0]);
        animLeft = new Animation<>(0.15f, frames[1]);
        animRight = new Animation<>(0.15f, frames[2]);
        animUp = new Animation<>(0.15f, frames[3]);

        currentAnimation = animDown;

        // BACKGROUND - Tiled
        background = new Texture("background.png");
        background.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);

        // Inicializar posición del joystick según tamaño de pantalla
        float padding = 150f;
        joystickCenter.set(padding, padding);
        joystickKnob.set(padding, padding);

        // WEBSOCKET - Determinamos la dirección según la plataforma
        String address;
        if (Gdx.app.getType() == Application.ApplicationType.Android) {
            address = "10.0.2.2"; // Emulador Android
        } else {
            address = "localhost"; // Desktop
        }

        Gdx.app.log("WebSocket", "Conectando a: " + address + ":8888");

        socket = WebSockets.newSocket(WebSockets.toWebSocketUrl(address, 8888));
        socket.setSendGracefully(false);

        // Añadimos un listener para saber el estado de la conexión
        socket.addListener(new WebSocketListener() {
            @Override
            public boolean onOpen(WebSocket webSocket) {
                Gdx.app.log("WebSocket", "¡Conectado al servidor!");
                socketConnected = true;
                return true;
            }

            @Override
            public boolean onClose(WebSocket webSocket, int closeCode, String reason) {
                Gdx.app.log("WebSocket", "Desconectado: " + reason);
                socketConnected = false;
                return true;
            }

            @Override
            public boolean onMessage(WebSocket webSocket, String packet) {
                Gdx.app.log("WebSocket", "Mensaje recibido: " + packet);
                return true;
            }

            @Override
            public boolean onMessage(WebSocket webSocket, byte[] packet) {
                Gdx.app.log("WebSocket", "Mensaje binario recibido");
                return true;
            }

            @Override
            public boolean onError(WebSocket webSocket, Throwable error) {
                Gdx.app.error("WebSocket", "Error: " + error.getMessage());
                socketConnected = false;
                return true;
            }
        });

        socket.connect();
    }

    @Override
    public void render() {

        float delta = Gdx.graphics.getDeltaTime();

        // Actualizar cámara
        camera.update();
        batch.setProjectionMatrix(camera.combined);
        shapeRenderer.setProjectionMatrix(camera.combined);

        Gdx.gl.glClearColor(0.15f, 0.15f, 0.2f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);

        handleInput(delta);

        // SOLO actualizar la animación si se está moviendo
        if (joystickActive) {
            stateTime += delta;
        } else {
            stateTime = 0f; // Volver al frame de pie si se suelta el joystick
        }
        TextureRegion currentFrame = currentAnimation.getKeyFrame(stateTime, true);

        // SEND POSITION (1 sec)
        timer += delta;
        if (timer > 1f) {
            timer = 0f;
            if (socket != null && socketConnected) {
                // Formato JSON para un toque más profesional
                String message = String.format("{\"x\": %d, \"y\": %d}", (int) posX, (int) posY);
                socket.send(message);
                Gdx.app.log("WebSocket", "Enviando: " + message);
            }
        }

        // 1. PINTAR FONDO REPETIDO
        batch.begin();
        // Usamos el tamaño de pantalla literal en srcW y srcH para que se repita
        // perfectamente
        // al tamaño original de la imagen, sin estirarse.
        batch.draw(background, 0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight(),
                0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), false, false);

        // 2. PINTAR JUGADOR
        batch.draw(currentFrame, posX, posY, currentFrame.getRegionWidth() * 1.5f,
                currentFrame.getRegionHeight() * 1.5f);
        batch.end();

        // 3. PINTAR JOYSTICK (SHAPE RENDERER)
        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        // Base del joystick (semi-transparente)
        shapeRenderer.setColor(0.5f, 0.5f, 0.5f, 0.4f);
        shapeRenderer.circle(joystickCenter.x, joystickCenter.y, joystickOuterRadius);

        // Pomo del joystick
        if (joystickActive) {
            shapeRenderer.setColor(Color.CYAN);
        } else {
            shapeRenderer.setColor(Color.LIGHT_GRAY);
        }
        shapeRenderer.circle(joystickKnob.x, joystickKnob.y, joystickInnerRadius);

        shapeRenderer.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    private void handleInput(float delta) {
        if (!Gdx.input.isTouched()) {
            joystickActive = false;
            joystickKnob.set(joystickCenter);
            moveVector.set(0, 0);
            return;
        }

        Vector3 touch = new Vector3(Gdx.input.getX(), Gdx.input.getY(), 0);
        // No necesitamos corregir Y manualmente si usamos el joystick lógico bien
        // Pero para ser consistentes con LibGDX Screen Coords vs World Coords:
        float touchX = touch.x;
        float touchY = Gdx.graphics.getHeight() - touch.y;

        float dist = joystickCenter.dst(touchX, touchY);

        if (dist < joystickOuterRadius || joystickActive) {
            joystickActive = true;

            if (dist > joystickOuterRadius) {
                // Limitar el pomo al radio exterior
                float angle = MathUtils.atan2(touchY - joystickCenter.y, touchX - joystickCenter.x);
                joystickKnob.x = joystickCenter.x + MathUtils.cos(angle) * joystickOuterRadius;
                joystickKnob.y = joystickCenter.y + MathUtils.sin(angle) * joystickOuterRadius;
            } else {
                joystickKnob.set(touchX, touchY);
            }

            // Calcular vector de movimiento normalizado
            moveVector.set(joystickKnob).sub(joystickCenter).scl(1f / joystickOuterRadius);

            // Mover personaje
            posX += moveVector.x * speed * delta;
            posY += moveVector.y * speed * delta;

            // Cambiar animación según dirección predominante SIN crear nuevos objetos
            if (Math.abs(moveVector.x) > Math.abs(moveVector.y)) {
                currentAnimation = moveVector.x > 0 ? animRight : animLeft;
            } else {
                currentAnimation = moveVector.y > 0 ? animUp : animDown;
            }
        }
    }

    @Override
    public void resize(int width, int height) {
        if (camera != null) {
            camera.setToOrtho(false, width, height);
        }
        // Reposicionar joystick si cambia el tamaño
        joystickCenter.set(150, 150);
        joystickKnob.set(150, 150);
    }

    @Override
    public void dispose() {
        batch.dispose();
        shapeRenderer.dispose();
        sheet.dispose();
        background.dispose();

        if (socket != null) {
            socket.close();
        }
    }
}