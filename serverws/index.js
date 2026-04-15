const WebSocket = require("ws");

// Creamos servidor en el puerto 8888
const server = new WebSocket.Server({ port: 8888 });

console.log("Servidor WebSocket activo en puerto 8888");

// Cuando un cliente se conecta
server.on("connection", (ws) => {
    console.log("Cliente conectado");

    // Cuando recibe mensaje
    ws.on("message", (message) => {
        console.log("Posición recibida:", message.toString());
    });

    // Cuando se desconecta
    ws.on("close", () => {
        console.log("Cliente desconectado");
    });
});