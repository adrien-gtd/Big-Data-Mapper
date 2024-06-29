package rs;

import java.util.List;
import java.util.concurrent.CountDownLatch;

public class Master {
    
    public static void main(String[] args) {
        int ftpPort = 5002;
        int socketPort = 5003;
        String username = "aguittard-22";
        String password = "tata";

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



        Splitter splitter = new Splitter("../source_file/source.txt", "../splitted_files/", serverConnection.getServers(), ftpPort, username, password);
        splitter.splitFile();
        splitter.distributeFiles();

        System.out.println("All files have been distributed, waiting for all servers to map");

        try {
            clientHandler.getFreezeMain().await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        System.out.println("All servers finished mapping, starting the shuffle phase");
        clientHandler.setMappingDone(true);

        clientHandler.sendMessageToAll("Start shuffle");

        System.out.println("Shuffle phase started, waiting for all servers to finish");
        clientHandler.setFreezeMain(new CountDownLatch(serverConnection.getServers().size()));

        try {
            clientHandler.getFreezeMain().await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        System.out.println("All servers finished shuffling, starting the reduce phase");

        clientHandler.sendMessageToAll("Start reduce");

        System.out.println("Reduce phase started, waiting for all servers to finish");

        clientHandler.setFreezeMain(new CountDownLatch(serverConnection.getServers().size()));

        try {
            clientHandler.getFreezeMain().await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        System.out.println("All servers finished reducing, starting the merge phase");

        Pair<Integer, Integer> maxRange = getGlobalMinMax(clientHandler.getRanges());

        System.out.println("Global min = " + maxRange.getKey() + ", global max = " + maxRange.getValue());
        clientHandler.sendMessageToAll("Start merge, maxRange = " + maxRange.getKey() + " " + maxRange.getValue());
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
}
