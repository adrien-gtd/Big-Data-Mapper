package rs;

import org.apache.ftpserver.FtpServer;

public class Main {
    private int ftpPort = 5002;
    private int socketPort = 5003;


    public void start() {
        FtpServer ftpServer = MyFTPServer.createServer(ftpPort);
        AsyncServer socketServer = new AsyncServer(socketPort, this);
    }

    public static void main(String[] args) {
        Main main = new Main();
        main.start();
    }
}
