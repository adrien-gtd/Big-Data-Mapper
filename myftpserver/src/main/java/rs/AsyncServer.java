package rs;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

public class AsyncServer {

    private AsynchronousServerSocketChannel serverSocket;
    private Main main;
    private Map<Integer, AsynchronousSocketChannel> clients = new ConcurrentHashMap<>();
    private int clientIdCounter = 1;

    public AsyncServer(int port, Main main) {
        try {
            serverSocket = AsynchronousServerSocketChannel.open().bind(new InetSocketAddress(port));
        } catch (IOException e) {
            e.printStackTrace();
        }
        this.main = main;
        start();
    }

    public void start() {
        System.out.println("Server is waiting to accept user...");
        CompletableFuture<Void> future = new CompletableFuture<>();

        serverSocket.accept(null, new CompletionHandler<AsynchronousSocketChannel, Void>() {
            @Override
            public void completed(AsynchronousSocketChannel socketChannel, Void attachment) {
                serverSocket.accept(null, this); // accept next connection
                int clientId = clientIdCounter++;
                clients.put(clientId, socketChannel);
                handleClient(socketChannel, clientId);
            }

            @Override
            public void failed(Throwable exc, Void attachment) {
                future.completeExceptionally(exc);
            }
        });

        try {
            future.get(); // wait until server stops
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
    }

    private void handleClient(AsynchronousSocketChannel socketChannel, int clientId) {
        try {
            System.out.println("Accept a client!");

            ByteBuffer buffer = ByteBuffer.allocate(1024);
            StringBuilder messageBuilder = new StringBuilder();

            while (socketChannel.read(buffer).get() != -1) {
                buffer.flip();
                CharBuffer charBuffer = StandardCharsets.UTF_8.decode(buffer);
                messageBuilder.append(charBuffer);

                String message = messageBuilder.toString().trim();
                System.out.println("Received message: " + message);

                // Process the received message
                onReceive(socketChannel, message);

                buffer.clear();
                messageBuilder.setLength(0);
            }
        } catch (InterruptedException | ExecutionException | IOException e) {
            if (!(e.getCause() instanceof AsynchronousCloseException)) {
                e.printStackTrace();
            }
        } finally {
            try {
                socketChannel.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void onReceive(AsynchronousSocketChannel socketChannel, String message) throws IOException {
        ByteBuffer buffer = StandardCharsets.UTF_8.encode(">> " + message + "\n");
        socketChannel.write(buffer);
        buffer.clear();
        main.receivedMessage(message);
        sendMessageToClient(1, "OK");
    }

    public void sendMessageToClient(int clientId, String message) {
        AsynchronousSocketChannel clientChannel = clients.get(clientId);
        if (clientChannel != null) {
            ByteBuffer buffer = StandardCharsets.UTF_8.encode("Server: " + message + "\n");
            clientChannel.write(buffer);
            buffer.clear();
        } else {
            System.out.println("Client with ID " + clientId + " not found.");
        }
    }
}