package rs;

import java.util.concurrent.CountDownLatch;

import org.apache.ftpserver.FtpServer;

public class Main {
    private int ftpPort = 5002;
    private int socketPort = 5003;

    public void start() {
        FtpServer ftpServer = MyFTPServer.createServer(ftpPort);
        CountDownLatch latch = new CountDownLatch(1);
        AsyncServer socketServer = new AsyncServer(socketPort, this, latch);
        Thread serverThread = new Thread(socketServer);
        serverThread.start();

        try {
            latch.await();  // Wait for the server to be fully created and client to be connected
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        System.out.println("Sending a message to the client!");
        socketServer.sendMessageToClient("Hello from main");
    }

    public static void main(String[] args) {
        Main main = new Main();
        main.start();
    }

    public void receivedRequest(String request) {
        System.out.println("Request received: " + request);
    }
}
