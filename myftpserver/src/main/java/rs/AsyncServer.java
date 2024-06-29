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
import java.util.concurrent.CountDownLatch;

public class AsyncServer implements Runnable {

    private AsynchronousServerSocketChannel serverSocket;
    private Main main;
    private AsynchronousSocketChannel clientSocket;
    private boolean clientConnected = false;
    private CountDownLatch latch;
    private int port;

    public AsyncServer(int port, Main main, CountDownLatch latch) {
        this.port = port;
        this.main = main;
        this.latch = latch;
    }

    @Override
    public void run() {
        try {
            serverSocket = AsynchronousServerSocketChannel.open().bind(new InetSocketAddress(port));
        } catch (IOException e) {
            e.printStackTrace();
        }
        start();
    }

    public void start() {
        System.out.println("Server is waiting to accept user...");

        serverSocket.accept(null, new CompletionHandler<AsynchronousSocketChannel, Void>() {
            @Override
            public void completed(AsynchronousSocketChannel socketChannel, Void attachment) {
                if (clientConnected) {
                    System.out.println("Client connection attempt rejected: another client is already connected.");
                    try {
                        socketChannel.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    return;
                }

                clientConnected = true;
                clientSocket = socketChannel;
                System.out.println("Accepted a client!");
                latch.countDown();  // Signal that the client has connected
                handleClient(socketChannel);
            }

            @Override
            public void failed(Throwable exc, Void attachment) {
                exc.printStackTrace();
            }
        });
    }

    private void handleClient(AsynchronousSocketChannel socketChannel) {
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        socketChannel.read(buffer, buffer, new CompletionHandler<Integer, ByteBuffer>() {
            @Override
            public void completed(Integer result, ByteBuffer buffer) {
                if (result == -1) {
                    try {
                        socketChannel.close();
                        clientConnected = false; // Allow new client connection
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    return;
                }
                buffer.flip();
                CharBuffer charBuffer = StandardCharsets.UTF_8.decode(buffer);
                String message = charBuffer.toString().trim();
                buffer.clear();

                try {
                    onReceive(socketChannel, message);
                } catch (IOException e) {
                    e.printStackTrace();
                }

                socketChannel.read(buffer, buffer, this); // Read next message
            }

            @Override
            public void failed(Throwable exc, ByteBuffer buffer) {
                if (!(exc instanceof AsynchronousCloseException)) {
                    exc.printStackTrace();
                }
                try {
                    socketChannel.close();
                    clientConnected = false; // Allow new client connection
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private void onReceive(AsynchronousSocketChannel socketChannel, String message) throws IOException {
        main.receivedRequest(message);
        System.out.println("Received message: " + message);
    }

    public void sendMessageToClient(String message) {
        if (clientSocket != null && clientSocket.isOpen()) {
            ByteBuffer buffer = StandardCharsets.UTF_8.encode("Server: " + message + "\n");
            clientSocket.write(buffer, buffer, new CompletionHandler<Integer, ByteBuffer>() {
                @Override
                public void completed(Integer result, ByteBuffer buffer) {
                    // Message sent
                }

                @Override
                public void failed(Throwable exc, ByteBuffer buffer) {
                    exc.printStackTrace();
                }
            });
        } else {
            System.out.println("No client is connected.");
        }
    }
}
