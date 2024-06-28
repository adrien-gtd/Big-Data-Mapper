package rs;


import java.io.BufferedReader;
import java.io.FileReader;
import java.net.Socket;
import java.util.ArrayList;

public class MyFTPClient {
    
    public static final String nodesFile = "../nodes.txt";


    public static void main(String[] args) {

        int ftpPort = 5002;
        int socketPort = 5003;
        String username = "aguittard-22";
        String password = "tata";
        ArrayList<String> servers = new ArrayList<String>();
        ArrayList<Socket> serversSockets = new ArrayList<Socket>();

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

        Splitter splitter = new Splitter("../source_file/source.txt", "../splitted_files/", servers, ftpPort, username, password);
        splitter.splitFile();
        splitter.distributeFiles();



    //     FTPClient ftpClient = new FTPClient();
    //     try {
    //         ftpClient.connect(server, port);
    //         ftpClient.login(username, password);
    //         ftpClient.enterLocalPassiveMode();
    //         ftpClient.setFileType(FTP.BINARY_FILE_TYPE);

    //         // Code to display files
    //         FTPFile[] files = ftpClient.listFiles();
    //         boolean fileExists = false;
    //         for (FTPFile file : files) {
    //             if (file.getName().equals("bonjour.txt")) {
    //                 fileExists = true;
    //                 break;
    //             }
    //         }

    //         if (!fileExists) {
    //             String content = "bonjour toto";
    //             ByteArrayInputStream inputStream = new ByteArrayInputStream(content.getBytes());
    //             ftpClient.storeFile("bonjour.txt", inputStream);
    //             int errorCode = ftpClient.getReplyCode();
    //             if (errorCode != 226) {
    //                 System.out.println("File upload failed. FTP Error code: " + errorCode);
    //             } else {
    //                 System.out.println("File uploaded successfully.");
    //             }
    //         } else {
    //             // Code to retrieve and display file content
            
    //                 InputStream inputStream = ftpClient.retrieveFileStream("bonjour.txt");
    //                 BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
    //                 String line;
    //                 while ((line = reader.readLine()) != null) {
    //                     System.out.println(line);
    //                 }
    //                 reader.close();
    //                 ftpClient.completePendingCommand();
                
    //         }

    //         ftpClient.logout();
    //         ftpClient.disconnect();
    //     } catch (Exception e) {
    //         e.printStackTrace();
    //     }
    }
}


