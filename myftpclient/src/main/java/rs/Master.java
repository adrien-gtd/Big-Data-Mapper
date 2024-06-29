package rs;

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

        // Now it's safe to send messages
        clientHandler.sendMessageToAll("Hello to all servers");

        System.out.println("All servers have been initialized");

        System.out.println("Servers:");
        for (String server : serverConnection.getServers()) {
            System.out.println("Server: " + server);
        }

        Splitter splitter = new Splitter("../source_file/source.txt", "../splitted_files/", serverConnection.getServers(), ftpPort, username, password);
        splitter.splitFile();
        splitter.distributeFiles();

        System.out.println("All files have been distributed");

        while (true);
    }
}
