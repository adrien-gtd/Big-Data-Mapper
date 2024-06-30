package rs;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;





public class TaskHandler {
    private String username;
    private String password;
    private int port;


    private ArrayList<String> servers = new ArrayList<String>();

    public TaskHandler(String nodesFile, String username, String password, int port) {
        this.username = username;
        this.password = password;
        this.port = port;

        try (BufferedReader reader = new BufferedReader(new FileReader(nodesFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                servers.add(line);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void startMapping(String inputFile) {
        File file = new File(inputFile);
        String hostname;
        try {
            InetAddress localHost = InetAddress.getLocalHost();
            hostname = localHost.getHostName();
        } catch (UnknownHostException e) {
            hostname = "Unknown";
            e.printStackTrace();
        }

        ArrayList<BufferedWriter> shuffleFiles = new ArrayList<BufferedWriter>();
        try {
            String filePath;
            for (String server : servers) {
                if (server.equals(hostname)) {
                    filePath = "shuffle_files/shuffle_local.txt";
                    Path path = Paths.get(filePath).getParent();
                    if (path != null) 
                        Files.createDirectories(path);
                    shuffleFiles.add(new BufferedWriter(new FileWriter(filePath)));
                } else {
                    filePath = "shuffle_" + server + "_from_" + hostname + ".txt";
                    shuffleFiles.add(new BufferedWriter(new FileWriter(filePath)));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;

            while ((line = reader.readLine()) != null) {
                // Split the line into words using whitespace as the delimiter
                String[] words = line.split("\\s+");

                for (String word : words) {
                    processWord(word, shuffleFiles);
                }
            }
            for (BufferedWriter writer : shuffleFiles) {
                writer.flush();
                writer.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        int numSplits = shuffleFiles.size() - 1;
        int fileNumber = 1;
        for (String server : servers) {
            if (!server.equals(hostname)) {
                FTPClient ftpClient = new FTPClient();
                try {
                    ftpClient.connect(server, port);
                    ftpClient.login(username, password);
                    ftpClient.enterLocalPassiveMode();
                    ftpClient.setFileType(FTP.BINARY_FILE_TYPE);
                    String remoteDirectory = "/shuffle_files";

                    boolean directoryExists = ftpClient.changeWorkingDirectory(remoteDirectory);
                    if (!directoryExists) {
                        boolean created = ftpClient.makeDirectory(remoteDirectory);
                        if (created) {
                            System.out.println(
                                    "[" + hostname + "]Remote directory created successfully: " + remoteDirectory);
                        } else {
                            System.out
                                    .println("[" + hostname + "]Failed to create remote directory: " + remoteDirectory);
                            return; // Exit if directory creation failed
                        }
                    }

                    ftpClient.storeFile(remoteDirectory + "/shuffle_" + server + "_from_" + hostname + ".txt",
                            new FileInputStream("shuffle_" + server + "_from_" + hostname + ".txt"));

                    int errorCode = ftpClient.getReplyCode();
                    if (errorCode != 226) {
                        System.out.println("[" + hostname + "]File [" + (fileNumber) + "/" + numSplits
                                + "]upload failed. FTP Error code: " + errorCode);
                    } else {
                        System.out.println("[" + hostname + "]File uploaded successfully to client number ["
                                + (fileNumber++) + "/" + numSplits + "]: " + server);
                    }
                    ftpClient.logout();
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    try {
                        ftpClient.disconnect();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    private void processWord(String word, ArrayList<BufferedWriter> shuffleFiles) {
        int serverIndex = Math.abs(word.hashCode()) % servers.size();
        BufferedWriter writer = shuffleFiles.get(serverIndex);
        try {
            writer.write(word + " 1");
            writer.newLine();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void startReduce(Main main, String directoryPath) {
        // Create a HashMap to store word occurrences
        Map<String, Integer> wordOccurrences = new HashMap<>();

        try {
            // Open the directory using NIO
            Path dirPath = Paths.get(directoryPath);
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dirPath)) {
                // Iterate over each file in the directory
                for (Path filePath : stream) {
                    // Process each file
                    if (directoryPath.equals("./shuffle_files"))
                        processFileShuffle(filePath, wordOccurrences);
                    if (directoryPath.equals("./reduced_files"))
                        processFileReduced(filePath, wordOccurrences);
                    else 
                        System.out.println("Unknown directory path: " + directoryPath);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (directoryPath.equals("./shuffle_files"))
            main.reduceCompleted(wordOccurrences);
        if (directoryPath.equals("./reduced_files")) {
            List<Map.Entry<String, Integer>> list = new ArrayList<>(wordOccurrences.entrySet());
            list.sort(Map.Entry.comparingByValue(Comparator.reverseOrder()));
            try (BufferedWriter writer = new BufferedWriter(new FileWriter("output.txt"))) {
                for (Map.Entry<String, Integer> entry : list) {
                    writer.write(entry.getKey() + " " + entry.getValue());
                    writer.newLine();
                }
                writer.flush();
                writer.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static void processFileShuffle(Path filePath, Map<String, Integer> wordOccurrences) throws IOException {
        System.out.println("Processing file: " + filePath);
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath.toFile()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // Split the line into word and occurrence parts
                String[] parts = line.split("\\s");
                if (parts.length == 2) {
                    String word = parts[0].trim();
                    int occurrence = Integer.parseInt(parts[1].trim());

                    // Update the wordOccurrences map
                    if (wordOccurrences.containsKey(word)) {
                        wordOccurrences.put(word, wordOccurrences.get(word) + occurrence);
                    } else {
                        wordOccurrences.put(word, occurrence);
                    }
                }
            }
        }
    }

    public void startMergePhase (Pair<Integer, Integer> globalMinMax, Map<String, Integer> wordOccurrences) {
        String hostname;
        try {
            InetAddress localHost = InetAddress.getLocalHost();
            hostname = localHost.getHostName();
        } catch (UnknownHostException e) {
            hostname = "Unknown";
            e.printStackTrace();
        }

        ArrayList<BufferedWriter> reducedFiles = new ArrayList<BufferedWriter>();
        try {
            String filePath;
            for (String server : servers) {
                if (server.equals(hostname)) {
                    filePath = "reduced_files/reduced_local.txt";
                    Path path = Paths.get(filePath).getParent();
                    if (path != null) 
                        Files.createDirectories(path);
                        reducedFiles.add(new BufferedWriter(new FileWriter(filePath)));
                } else {
                    filePath = "reduced_" + server + "_from_" + hostname + ".txt";
                    reducedFiles.add(new BufferedWriter(new FileWriter(filePath)));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        int min = globalMinMax.getKey();
        int max = globalMinMax.getValue();
        int n = servers.size();
        int range = (max - min + 1) / n;

        try {
            for (Entry<String, Integer> pair : wordOccurrences.entrySet()) {
                int index = (pair.getValue() - min) / range;
                if (index >= n) {
                    index = n - 1; // Ensure the last bucket includes the upper boundary values
                }
                reducedFiles.get(index).write(pair.getKey() + " " + pair.getValue());
                reducedFiles.get(index).newLine();
            }
            for (BufferedWriter writer : reducedFiles) {
                writer.flush();
                writer.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        int numSplits = reducedFiles.size() - 1;
        int fileNumber = 1;
        for (String server : servers) {
            if (!server.equals(hostname)) {
                FTPClient ftpClient = new FTPClient();
                try {
                    ftpClient.connect(server, port);
                    ftpClient.login(username, password);
                    ftpClient.enterLocalPassiveMode();
                    ftpClient.setFileType(FTP.BINARY_FILE_TYPE);
                    String remoteDirectory = "/reduced_files";

                    boolean directoryExists = ftpClient.changeWorkingDirectory(remoteDirectory);
                    if (!directoryExists) {
                        boolean created = ftpClient.makeDirectory(remoteDirectory);
                        if (created) {
                            System.out.println(
                                    "[" + hostname + "]Remote directory created successfully: " + remoteDirectory);
                        } else {
                            System.out
                                    .println("[" + hostname + "]Failed to create remote directory: " + remoteDirectory);
                            return; // Exit if directory creation failed
                        }
                    }

                    ftpClient.storeFile(remoteDirectory + "/reduced_" + server + "_from_" + hostname + ".txt",
                            new FileInputStream("reduced_" + server + "_from_" + hostname + ".txt"));

                    int errorCode = ftpClient.getReplyCode();
                    if (errorCode != 226) {
                        System.out.println("[" + hostname + "]File [" + (fileNumber) + "/" + numSplits
                                + "]upload failed. FTP Error code: " + errorCode);
                    } else {
                        System.out.println("[" + hostname + "]File uploaded successfully to client number ["
                                + (fileNumber++) + "/" + numSplits + "]: " + server);
                    }
                    ftpClient.logout();
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    try {
                        ftpClient.disconnect();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    public void processFileReduced(Path filePath, Map<String, Integer> wordOccurrences) throws IOException {    // Very similar to processFileShuffle but no need to update the wordOccurrences map
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath.toFile()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // Split the line into word and occurrence parts
                String[] parts = line.split("\\s");
                if (parts.length == 2) {
                    String word = parts[0].trim();
                    int occurrence = Integer.parseInt(parts[1].trim());
                    wordOccurrences.put(word, occurrence);
                }
            }
        }
    }

}
