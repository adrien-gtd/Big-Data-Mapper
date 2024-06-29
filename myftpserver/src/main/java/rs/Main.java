package rs;

import java.util.concurrent.CountDownLatch;

public class Main {
    private int ftpPort = 5002;
    private int socketPort = 5003;
    private AsyncServer socketServer;
    private String ftpUsername = "aguittard-22";
    private String ftpPassword = "tata";
    private TaskHandler taskHandler;
    private CountDownLatch mapCountDownLatch = new CountDownLatch(2);

    public void start() {
        MyFTPServer.createServer(ftpPort, this, ftpUsername, ftpPassword);
        CountDownLatch latch = new CountDownLatch(1);
        socketServer = new AsyncServer(socketPort, this, latch);
        Thread serverThread = new Thread(socketServer);
        serverThread.start();

        
        try {
            latch.await();  // Wait for the server to be fully created and client to be connected
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        System.out.println("Waiting for input file...");

        try {
            mapCountDownLatch.await();  // Wait for the task handler to be initialized
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        System.out.println("Input file received, waiting to start shuffle...");

        socketServer.sendMessageToClient("File received");
    }

    public static void main(String[] args) {
        Main main = new Main();
        main.start();
    }

    public void receivedRequest(String request) {
        System.out.println("Request received: " + request);
        if (request.equals("Start shuffle")) 
            startShuffle();
        else if (request.equals("Start reduce"))
            startReduce();
    }

    public void receivedInputFile() {
        mapCountDownLatch.countDown();
    }

    public void startShuffle() {
        taskHandler.startMapping("input.txt");
        socketServer.sendMessageToClient("Shuffle done");
    }

    public void initTaskHandler() {
        this.taskHandler = new TaskHandler("nodes.txt", ftpUsername, ftpPassword, ftpPort);
        mapCountDownLatch.countDown();
    }

    public void startReduce() {
        taskHandler.startReduce();
    }
}
