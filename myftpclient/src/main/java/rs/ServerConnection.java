package rs;


import java.io.BufferedReader;
import java.io.FileReader;
import java.net.Socket;
import java.util.ArrayList;

public class ServerConnection {
    
    public static final String nodesFile = "../nodes.txt";
    private ArrayList<String> servers = new ArrayList<String>();
    private ArrayList<Socket> serversSockets = new ArrayList<Socket>();


    public ServerConnection(int ftpPort, int socketPort, String ftpUsername, String ftpPassword) {

        servers = new ArrayList<String>();
        serversSockets = new ArrayList<Socket>();

        try (BufferedReader reader = new BufferedReader(new FileReader(nodesFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                servers.add(line);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        for (String server : servers) {
            try {
                Socket socket = new Socket(server, socketPort);
                serversSockets.add(socket);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public ArrayList<String> getServers() {
        return servers;
    }

    public ArrayList<Socket> getServersSockets() {
        return serversSockets;
    }

    public void closeConnections() {
        for (Socket socket : serversSockets) {
            try {
                socket.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}


