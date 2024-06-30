package rs;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import org.apache.ftpserver.FtpServer;

public class Main {
    private int ftpPort = 5002;
    private int socketPort = 5003;
    private AsyncServer socketServer;
    private FtpServer ftpServer;
    private String ftpUsername = "aguittard-22";
    private String ftpPassword = "tata";
    private TaskHandler taskHandler;
    private CountDownLatch mapCountDownLatch = new CountDownLatch(2);
    private Map<String, Integer> reduceResult;

    public void start() {
        ftpServer = MyFTPServer.createServer(ftpPort, this, ftpUsername, ftpPassword);
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
        taskHandler.startReduce(this);
    }

    public void reduceCompleted(Map<String, Integer> reduceResult) {
        // Extract values
        Collection<Integer> values = reduceResult.values();
        
        // Get min and max values
        int minValue = Collections.min(values);
        int maxValue = Collections.max(values);
        this.reduceResult = reduceResult;
        socketServer.sendMessageToClient("Reduce done: min = " + minValue + ", max = " + maxValue);
        System.out.println("Reduce done, waiting for global min/max...");
    }

    public void startMerge () {
        System.out.println("Global min/max received, starting reduce phase...");
        taskHandler.startMergePhase(socketServer.getGlobalMinMax(), reduceResult);
        System.out.println("Merge done, waiting to start merging phase...");
        socketServer.sendMessageToClient("Merge done");
    }

    public void startSort() {
        taskHandler.startSortPhase();
        System.out.println("Sort done, waiting answer from client to shutdown...");
        socketServer.sendMessageToClient("Sort done");
    }

    public void stop() {
        System.out.println("End of the process");
        ftpServer.stop();
        socketServer.stop();
    }
}
