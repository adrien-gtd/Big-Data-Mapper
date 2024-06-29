package rs;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.ftpserver.FtpServer;
import org.apache.ftpserver.FtpServerFactory;
import org.apache.ftpserver.ftplet.Authority;
import org.apache.ftpserver.ftplet.DefaultFtplet;
import org.apache.ftpserver.ftplet.FtpException;
import org.apache.ftpserver.ftplet.FtpRequest;
import org.apache.ftpserver.ftplet.FtpSession;
import org.apache.ftpserver.ftplet.Ftplet;
import org.apache.ftpserver.ftplet.FtpletResult;
import org.apache.ftpserver.ftplet.UserManager;
import org.apache.ftpserver.listener.ListenerFactory;
import org.apache.ftpserver.usermanager.ClearTextPasswordEncryptor;
import org.apache.ftpserver.usermanager.PropertiesUserManagerFactory;
import org.apache.ftpserver.usermanager.impl.BaseUser;
import org.apache.ftpserver.usermanager.impl.WritePermission;
import org.apache.log4j.PropertyConfigurator;

public class MyFTPServer {

    public static FtpServer createServer(int port, Main main, String ftpUsername, String ftpPassword) {
        PropertyConfigurator.configure(MyFTPServer.class.getResource("/log4J.properties"));
        FtpServerFactory serverFactory = new FtpServerFactory();

        ListenerFactory listenerFactory = new ListenerFactory();
        listenerFactory.setPort(port);

        serverFactory.addListener("default", listenerFactory.createListener());

        // Create a UserManager instance
        PropertiesUserManagerFactory userManagerFactory = new PropertiesUserManagerFactory();
        File userFile = new File("users.properties");
        if (!userFile.exists()) {
            try {
                if (userFile.createNewFile()) {
                    System.out.println("File created: " + userFile.getName());
                } else {
                    System.out.println("File already exists.");
                }
            } catch (IOException e) {
                System.out.println("An error occurred.");
                e.printStackTrace();
            }
        }

        userManagerFactory.setFile(userFile); // Specify the file to store user details
        userManagerFactory.setPasswordEncryptor(new ClearTextPasswordEncryptor()); // Store plain text passwords
        UserManager userManager = userManagerFactory.createUserManager();
        // Create a user
        BaseUser user = new BaseUser();
        user.setName(ftpUsername); // Replace "username" with the desired username
        user.setPassword(ftpPassword); // Replace "password" with the desired password
        String username = user.getName();
        String tmpDir = System.getProperty("java.io.tmpdir");
        Path path = Paths.get(tmpDir, username);
        String homeDirectory = path.toString();

        File directory = new File(homeDirectory); // Convert the string to a File object
        if (!directory.exists()) { // Check if the directory exists
            if (directory.mkdirs()) {
                System.out.println("Directory created: " + directory.getAbsolutePath());
            } else {
                System.out.println("Failed to create directory.");
            }
        }
        user.setHomeDirectory(homeDirectory);
        // Set write permissions for the user
        List<Authority> authorities = new ArrayList<>();
        authorities.add(new WritePermission());
        user.setAuthorities(authorities);
        user.setHomeDirectory(homeDirectory);

        // Add the user to the user manager
        try {
            userManager.save(user);
        } catch (FtpException e) {
            e.printStackTrace();
        }
        // Set the user manager on the server context
        serverFactory.setUserManager(userManager);

        Map<String, Ftplet> ftplets = new HashMap<>();
        ftplets.put("customFtplet", new DefaultFtplet() {
            @Override
            public FtpletResult onUploadEnd(FtpSession session, FtpRequest request) throws FtpException, IOException {
                // Check the uploaded file name and trigger the action if it matches
                String fileName = request.getArgument();
                if (fileName.equals("input.txt")) {
                    System.out.println("Input file uploaded: " + fileName);
                    main.receivedInputFile();
                }
                if (fileName.equals("nodes.txt")) {
                    System.out.println("Nodes file uploaded: " + fileName);
                    main.initTaskHandler();
                }
                return FtpletResult.DEFAULT;
            }
        });
        serverFactory.setFtplets(ftplets);
        FtpServer server = serverFactory.createServer();

        // start the server
        try {
            server.start();
            System.out.println("FTP Server started on port " + port);
            
        } catch (FtpException e) {
            e.printStackTrace();
        }
        return server;
    }
}