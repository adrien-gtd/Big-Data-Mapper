package rs;

import org.apache.ftpserver.FtpServer;

public class Main {
    private int ftpPort = 5002;
    private int socketPort = 5003;
    private final Object socketLock = new Object(); // For synchronization

    private FtpServer ftpServer;
    private AsyncServer socketServer;

    public void start() {
        socketServer = new AsyncServer(socketPort, this);
        ftpServer = MyFTPServer.createServer(ftpPort);
    }

    public void receivedMessage(String message) {
        System.out.println("Received message, printing from main: " + message);
    }

    public static void main(String[] args) {
        Main main = new Main();
        main.start();
    }
}
