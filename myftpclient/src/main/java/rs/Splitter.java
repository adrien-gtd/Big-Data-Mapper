package rs;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;


import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;

public class Splitter {
    private final String sourceFile;
    private final String targetDir;
    private final List<String> servers;

    private final String username;
    private final String password;
    private final int port;

    private boolean isSplit = false;        // Check if the files are splitted

    private long numSplits;


    public Splitter (String sourceFile, String targetDir, List<String> servers, int port, String username, String password) {
        this.sourceFile = sourceFile;
        this.targetDir = targetDir;
        this.servers = servers;
        this.port = port;
        this.username = username;
        this.password = password;
        this.numSplits = servers.size();

    }

    private void removeTempFiles() {
        try {
            System.out.println("Removing files from" + targetDir);
            Files.list(Paths.get(targetDir))
                .filter(file -> Files.isRegularFile(file) && !file.getFileName().toString().equals(".gitkeep")) // Filter only regular files (not directories)
                .forEach(file -> {
                    try {
                        Files.delete(file); // Delete each file
                        System.out.println("Deleted file: " + file);
                    } catch (IOException e) {
                        System.err.println("Failed to delete file: " + file + " - " + e);
                    }
                });
            System.out.println("All files in directory deleted successfully.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void splitFile() {
        removeTempFiles();
        try {
            // Open input stream
            FileInputStream fis = new FileInputStream(sourceFile);
            BufferedReader reader = new BufferedReader(new InputStreamReader(fis));

            // Calculate approximate lines per split
            long totalLines = Files.lines(Paths.get(sourceFile)).count();
            long linesPerSplit = totalLines / numSplits;

            // Initialize variables
            int splitNumber = 1;
            String line;

            // Create new split file
            BufferedWriter writer = null;
            long linesWritten = 0;

            while ((line = reader.readLine()) != null) {
                if (linesWritten % linesPerSplit == 0) {
                    // Close current writer if exists
                    if (writer != null) {
                        writer.close();
                    }
                    
                    // Open new split file
                    String splitFileName = String.format("split_%03d.txt", splitNumber++);
                    String outputPath = targetDir + splitFileName;
                    File outputFile = new File(outputPath);
                    if (!outputFile.exists()) {
                        outputFile.createNewFile();
                        System.out.println("File created: " + outputPath);
                    }
                    writer = new BufferedWriter(new FileWriter(outputPath));
                }

                // Write line to current split file
                writer.write(line);
                writer.newLine();
                linesWritten++;
            }

            // Close final writer
            if (writer != null) {
                writer.close();
            }

            // Close streams
            reader.close();
            fis.close();

            System.out.println("File splitting completed.");
            System.out.println("Split files saved in: " + targetDir);

        } catch (IOException e) {
            e.printStackTrace();
        }
        isSplit = true;
    }



    public void distributeFiles () {
        if (!isSplit) {
            System.out.println("File not splitted yet.");
            return;
        }
        String splitFileName;
        int fileNumber = 1;
        for (String server : servers) {
            splitFileName = String.format(targetDir + "split_%03d.txt", fileNumber++);
            FTPClient ftpClient = new FTPClient();
            try {
                ftpClient.connect(server, port);
                ftpClient.login(username, password);
                ftpClient.enterLocalPassiveMode();
                ftpClient.setFileType(FTP.BINARY_FILE_TYPE);

                if (ftpClient.listFiles("input.txt").length > 0) {
                    ftpClient.deleteFile("input.txt");
                }

                InputStream inputStream = new FileInputStream(new File(splitFileName));
                ftpClient.storeFile("input.txt", inputStream);

                InputStream inputStreamNodes = new ByteArrayInputStream(String.join(System.lineSeparator(), servers).getBytes(StandardCharsets.UTF_8));
                ftpClient.storeFile("nodes.txt", inputStreamNodes);
                int errorCode = ftpClient.getReplyCode();
                if (errorCode != 226) {
                    System.out.println("File [" + (fileNumber-1) + "/" + numSplits + "]upload failed. FTP Error code: " + errorCode);
                } else {
                    System.out.println("File uploaded successfully to client number [" + (fileNumber-1) + "/" + numSplits + "]: " + server );
                }

                ftpClient.logout();
                ftpClient.disconnect();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        removeTempFiles();
    }
    

    public void retrieveOutputs(String OUTPUT_FILE, String REMOTE_FILE) {
        FTPClient ftpClient = new FTPClient();

        // Delete the output file if it already exists
        File file = new File(OUTPUT_FILE);
        if (file.exists()) {
            // Attempt to delete the file
            if (file.delete()) {
                System.out.println("File deleted successfully.");
            } else {
                System.out.println("Failed to delete the file.");
            }
        }

        // Ensure the output directory exists
        Path outputPath = Paths.get(OUTPUT_FILE);
        try {
            Files.createDirectories(outputPath.getParent());
        } catch (IOException e) {
            System.err.println("Failed to create output directory: " + e.getMessage());
            return;
        }

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(OUTPUT_FILE, true))) {
            for (int i = servers.size() - 1; i >= 0; i--) {
                String server = servers.get(i);
                try {
                    ftpClient.connect(server, port);
                    ftpClient.login(username, password);
                    ftpClient.enterLocalPassiveMode();

                    try (InputStream inputStream = ftpClient.retrieveFileStream(REMOTE_FILE)) {
                        if (inputStream != null) {
                            appendFileContent(writer, inputStream);
                            ftpClient.completePendingCommand();
                        } else {
                            System.err.println("Failed to retrieve file from " + server);
                        }
                    }

                    ftpClient.logout();
                } catch (IOException ex) {
                    System.err.println("Error retrieving file from server: " + server + " - " + ex.getMessage());
                } finally {
                    try {
                        ftpClient.disconnect();
                    } catch (IOException ex) {
                        System.err.println("Error disconnecting from server: " + server + " - " + ex.getMessage());
                    }
                }
            }
        } catch (IOException ex) {
            System.err.println("Error writing to local output file: " + ex.getMessage());
        }
    }

    private void appendFileContent(BufferedWriter writer, InputStream inputStream) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        String line;
        while ((line = reader.readLine()) != null) {
            writer.write(line);
            writer.newLine();
        }
    }
}
