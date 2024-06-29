package rs;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

public class ClientHandler implements Runnable {
    private ServerConnection serverConnection;
    private boolean running = true;
    private Map<Socket, BufferedWriter> writers = new HashMap<>();
    private CountDownLatch latch;

    public ClientHandler(ServerConnection serverConnection, CountDownLatch latch) {
        this.serverConnection = serverConnection;
        this.latch = latch;
    }

    @Override
    public void run() {
        for (Socket socket : serverConnection.getServersSockets()) {
            new Thread(() -> handleSocket(socket)).start();
        }
    }

    private void handleSocket(Socket socket) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
            synchronized (writers) {
                writers.put(socket, writer);
            }
            latch.countDown(); // Signal that this writer is initialized

            String line;
            while (running && (line = reader.readLine()) != null) {
                handleRequest(line);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            closeSocket(socket);
        }
    }

    private void handleRequest(String request) {
        System.out.println("Received message: " + request);
        // Handle the request here
    }

    public void sendMessageToAll(String message) {
        try {
            latch.await(); // Wait for all writers to be initialized
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        synchronized (writers) {
            for (Map.Entry<Socket, BufferedWriter> entry : writers.entrySet()) {
                sendMessage(entry.getKey(), entry.getValue(), message);
            }
        }
    }

    public void sendMessage(Socket socket, String message) {
        BufferedWriter writer;
        synchronized (writers) {
            writer = writers.get(socket);
        }
        if (writer != null) {
            sendMessage(socket, writer, message);
        } else {
            System.out.println("No writer found for socket: " + socket);
        }
    }

    private void sendMessage(Socket socket, BufferedWriter writer, String message) {
        try {
            writer.write(message);
            writer.newLine();
            writer.flush();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void stop() {
        running = false;
        serverConnection.closeConnections();
        synchronized (writers) {
            for (Socket socket : writers.keySet()) {
                closeSocket(socket);
            }
        }
    }

    private void closeSocket(Socket socket) {
        try {
            synchronized (writers) {
                BufferedWriter writer = writers.remove(socket);
                if (writer != null) {
                    writer.close();
                }
            }
            socket.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
