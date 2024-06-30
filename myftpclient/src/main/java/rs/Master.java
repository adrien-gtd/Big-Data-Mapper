package rs;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public class Master {

    public static void main(String[] args) {
        int ftpPort = 5002;
        int socketPort = 5003;
        String username = "aguittard-22";
        String password = "tata";
        long synchronizationTime = 0;
        long computationTime = 0;
        String sourceFile = "../source_file/source.txt";
        String mesureOutput = "../data.csv";

        if (args.length == 1)
            sourceFile = args[0];
        else if (args.length == 2) {
            sourceFile = args[0];
            mesureOutput = args[1];
        }

        ServerConnection serverConnection = new ServerConnection(ftpPort, socketPort, username, password);

        int numberOfServers = serverConnection.getServersSockets().size();
        CountDownLatch latch = new CountDownLatch(numberOfServers);

        ClientHandler clientHandler = new ClientHandler(serverConnection, latch);
        new Thread(clientHandler).start();

        // Wait until all writers are initialized
        try {
            latch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        System.out.println("All servers have been initialized");

        System.out.println("Servers:");
        for (String server : serverConnection.getServers()) {
            System.out.println("Server: " + server);
        }

        clientHandler.setFreezeMain(new CountDownLatch(serverConnection.getServers().size()));

        long startTimer = System.currentTimeMillis();
        long globalStartTimer = startTimer;

        Splitter splitter = new Splitter(sourceFile, "../splitted_files/", serverConnection.getServers(), ftpPort,
                username, password);
        splitter.splitFile();
        splitter.distributeFiles();

        System.out.println("All files have been distributed, waiting for all servers to receive the files");

        try {
            clientHandler.getFreezeMain().await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        synchronizationTime += System.currentTimeMillis() - startTimer;
        startTimer = System.currentTimeMillis();

        System.out.println("All servers have received the files, starting the map+shuffle phase");

        clientHandler.sendMessageToAll("Start shuffle");

        System.out.println("Map phase started, waiting for all servers to finish");

        clientHandler.setFreezeMain(new CountDownLatch(serverConnection.getServers().size()));

        try {
            clientHandler.getFreezeMain().await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        computationTime += System.currentTimeMillis() - startTimer;

        System.out.println("All servers finished mapping, starting the shuffle phase");

        startTimer = System.currentTimeMillis();

        clientHandler.setFreezeMain(new CountDownLatch(serverConnection.getServers().size()));

        try {
            clientHandler.getFreezeMain().await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        System.out.println("All servers finished shuffling, starting the reduce phase");

        clientHandler.sendMessageToAll("Start reduce");

        synchronizationTime += System.currentTimeMillis() - startTimer;
        startTimer = System.currentTimeMillis();

        System.out.println("Reduce phase started, waiting for all servers to finish");

        clientHandler.setFreezeMain(new CountDownLatch(serverConnection.getServers().size()));

        try {
            clientHandler.getFreezeMain().await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        computationTime += System.currentTimeMillis() - startTimer;
        startTimer = System.currentTimeMillis();

        System.out.println("All servers finished reducing, starting the merge phase");

        Pair<Integer, Integer> maxRange = getGlobalMinMax(clientHandler.getRanges());

        clientHandler.sendMessageToAll("Start merge, maxRange = " + maxRange.getKey() + " " + maxRange.getValue());

        System.out.println("Merge phase started, waiting for all servers to finish");

        clientHandler.setFreezeMain(new CountDownLatch(serverConnection.getServers().size()));

        try {
            clientHandler.getFreezeMain().await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        synchronizationTime += System.currentTimeMillis() - startTimer;
        startTimer = System.currentTimeMillis();

        System.out.println("All servers finished merging, starting the sorting phase");

        clientHandler.sendMessageToAll("Start sort");

        System.out.println("Sort phase started, waiting for all servers to finish");

        clientHandler.setFreezeMain(new CountDownLatch(serverConnection.getServers().size()));

        try {
            clientHandler.getFreezeMain().await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        computationTime += System.currentTimeMillis() - startTimer;
        startTimer = System.currentTimeMillis();

        System.out.println("All servers finished sorting, retriving outputs and ending the program");

        splitter.retrieveOutputs("./output/output.txt", "output.txt");

        synchronizationTime += System.currentTimeMillis() - startTimer;
        long globalEndTimer = System.currentTimeMillis() - globalStartTimer;

        File file = new File(sourceFile);
        printLog(globalEndTimer, synchronizationTime, computationTime, mesureOutput, serverConnection.getServers().size(), file.length());

        clientHandler.sendMessageToAll("End");

        System.exit(0);
    }

    private static Pair<Integer, Integer> getGlobalMinMax(List<Pair<Integer, Integer>> ranges) {
        int globalMin = Integer.MAX_VALUE;
        int globalMax = Integer.MIN_VALUE;

        for (Pair<Integer, Integer> pair : ranges) {
            if (pair.getKey() < globalMin) {
                globalMin = pair.getKey();
            }
            if (pair.getValue() > globalMax) {
                globalMax = pair.getValue();
            }
        }

        return new Pair<>(globalMin, globalMax);
    }

    private static void printLog(long globalEndTimer, long synchronizationTime, long computationTime, String outputFilePath, int nb_machines, long size_input) {
        String line = String.valueOf(nb_machines) + "," + String.valueOf(size_input) + "," + String.valueOf(globalEndTimer) + "," + String.valueOf(synchronizationTime) + "," + String.valueOf(computationTime);
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFilePath, true))) {
            writer.write(line);
            writer.newLine();
        } catch (IOException e) {
            System.err.println("Error appending to file: " + e.getMessage());
        }

        System.out.println("Global time: " + globalEndTimer + " ms");
        System.out.println("Synchronization time: " + synchronizationTime + " ms");
        System.out.println("Computation time: " + computationTime + " ms");
    }
}
