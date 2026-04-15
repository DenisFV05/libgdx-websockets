package com.denis.ws;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector3;

import com.github.czyzby.websocket.WebSocket;
import com.github.czyzby.websocket.WebSockets;
import com.github.czyzby.websocket.CommonWebSockets;

public class MainWebsockets extends ApplicationAdapter {

    private SpriteBatch batch;

    // SPRITESHEET
    private Texture sheet;
    private TextureRegion[][] frames;
    private Animation<TextureRegion> animation;

    // BACKGROUND
    private Texture background;

    // PLAYER
    private float posX = 140;
    private float posY = 210;
    private float speed = 200;

    // ANIMATION
    private float stateTime = 0f;

    // JOYSTICK ZONES
    private Rectangle left, right, up, down;

    // WEBSOCKET
    private WebSocket socket;
    private float timer = 0f;

    @Override
    public void create() {

        batch = new SpriteBatch();

        // IMPORTANT: WebSockets init
        CommonWebSockets.initiate();

        // LOAD SPRITESHEET (5 columns, 4 rows)
        sheet = new Texture("player_sheet.png");

        frames = TextureRegion.split(
                sheet,
                sheet.getWidth() / 5,
                sheet.getHeight() / 4
        );

        // Fila 2 (ejemplo: derecha) → animación principal
        TextureRegion[] walk = new TextureRegion[4];

        for (int i = 0; i < 4; i++) {
            walk[i] = frames[2][i];
        }

        animation = new Animation<>(0.1f, walk);

        // BACKGROUND
        background = new Texture("background.png");

        // JOYSTICK ZONES
        left = new Rectangle(0, 0, Gdx.graphics.getWidth() / 3f, Gdx.graphics.getHeight());
        right = new Rectangle(Gdx.graphics.getWidth() * 2f / 3f, 0, Gdx.graphics.getWidth() / 3f, Gdx.graphics.getHeight());
        up = new Rectangle(0, Gdx.graphics.getHeight() * 2f / 3f, Gdx.graphics.getWidth(), Gdx.graphics.getHeight() / 3f);
        down = new Rectangle(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight() / 3f);

        // WEBSOCKET
        socket = WebSockets.newSocket(
                WebSockets.toWebSocketUrl("10.0.2.2", 8888)
        );

        socket.connect();
    }

    @Override
    public void render() {

        float delta = Gdx.graphics.getDeltaTime();

        Gdx.gl.glClearColor(0.15f, 0.15f, 0.2f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        stateTime += delta;

        TextureRegion currentFrame = animation.getKeyFrame(stateTime, true);

        handleInput(delta);

        // SEND POSITION (1 sec)
        timer += delta;

        if (timer > 1f) {
            timer = 0f;

            if (socket != null) {
                socket.send("x:" + posX + ",y:" + posY);
            }
        }

        batch.begin();

        // BACKGROUND FIRST
        batch.draw(background, 0, 0,
                Gdx.graphics.getWidth(),
                Gdx.graphics.getHeight());

        // PLAYER
        batch.draw(currentFrame, posX, posY);

        batch.end();
    }

    private void handleInput(float delta) {

        if (!Gdx.input.isTouched()) return;

        Vector3 touch = new Vector3();
        touch.set(Gdx.input.getX(), Gdx.input.getY(), 0);

        if (left.contains(touch.x, touch.y)) posX -= speed * delta;
        if (right.contains(touch.x, touch.y)) posX += speed * delta;
        if (up.contains(touch.x, touch.y)) posY += speed * delta;
        if (down.contains(touch.x, touch.y)) posY -= speed * delta;
    }

    @Override
    public void dispose() {

        batch.dispose();
        sheet.dispose();
        background.dispose();

        if (socket != null) socket.close();
    }
}
