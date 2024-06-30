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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AsyncServer implements Runnable {

    private AsynchronousServerSocketChannel serverSocket;
    private Main main;
    private AsynchronousSocketChannel clientSocket;
    private boolean clientConnected = false;
    private CountDownLatch latch;
    private int port;
    private Pair<Integer, Integer> globalMinMax;

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
                latch.countDown(); // Signal that the client has connected
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
        System.out.println("Request received: " + message);
        if (message.equals("Start shuffle"))
            main.startShuffle();
        else if (message.equals("Start reduce"))
            main.startReduce();
        else if (message.startsWith("Start merge")) {
            Pattern pattern = Pattern.compile("Start merge, maxRange = (\\d+) (\\d+)");
            Matcher matcher = pattern.matcher(message);
            if (matcher.find()) {
                int minValue = Integer.parseInt(matcher.group(1));
                int maxValue = Integer.parseInt(matcher.group(2));
                globalMinMax = new Pair<>(minValue, maxValue);
                main.startMerge();
            } else {
                throw new RuntimeException("Wrong message received, connot extract min and max values: " + message);
            }
        } else if (message.equals("Start sort"))
            main.startSort();
        else if (message.equals("End")) {
            socketChannel.close();
            main.stop();
        } else {
            System.out.println("Unknown request: " + message);
        }
    }

    public void sendMessageToClient(String message) {
        if (clientSocket != null && clientSocket.isOpen()) {
            ByteBuffer buffer = StandardCharsets.UTF_8.encode(message + "\n");
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

    public Pair<Integer, Integer> getGlobalMinMax() {
        return globalMinMax;
    }

    public void stop() {
        try {
            serverSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
