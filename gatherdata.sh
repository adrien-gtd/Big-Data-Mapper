#!/bin/bash
source config.txt

# Set the log file
serverLogFile="logs/server_logs.txt"
clientLogFile="logs/client_logs.txt"

> $serverLogFile
> $clientLogFile

# Define the nodes file that contains the list of all the hosts
allNodesFile="all_nodes.txt"

# Define the nodes files that will be created at each operation
nodesFiles="nodes.txt"

# List of hosts file that should contain each host in a separate line
computers=$(cat $allNodesFile | xargs -I {} echo {})

# Define the folder path for source files
folderPath="source_file"

# Get the list of filenames and store them in an array
fileArray=($(ls "$folderPath"))

# Loop through the array and prepend the folder path to each filename
for i in "${!fileArray[@]}"; do
  fileArray[$i]="../$folderPath/${fileArray[$i]}"
done

# SSH options to skip host key checking
sshopts="-o StrictHostKeyChecking=no"

# Remote folder where files will be transferred
remoteFolder="/tmp/$login/"
# Navigate to the myftpserver directory and build the project
cd myftpserver
echo "Compiling and packaging myftpserver project..."
mvn clean compile assembly:single
cd ..

# Build the myftpclient project
cd myftpclient
echo "Compiling and packaging myftpclient project..."
mvn clean compile assembly:single
cd ..

# Filename and extension of the JAR file to be transferred
fileName="myftpserver-1-jar-with-dependencies"
fileExtension=".jar"

# Filename and extension of the JAR file to be executed
clientFileName="myftpclient-1-jar-with-dependencies"
clientFileExtension=".jar"

# Remove the logs file if it already exists
rm -f $logFile

# Array to store process IDs of SSH commands
pids=()

loop_computers=()

# Number of times to loop
numTimes=5

# Empty the nodes file for the loop
> $nodesFiles

echo
# Loop through each computer in the list and perform SSH and SCP operations
for c in ${computers[@]}; do
    loop_computers+=($c)
    echo $c >> $nodesFiles
    for file in ${fileArray[@]}; do
        for ((i=1; i<=numTimes; i++)); do
            echo "========== Iteration: $i for file: $file most recent computer added $c =========="
            echo "========== Iteration: $i for file: $file most recent computer added $c ==========" >> $serverLogFile
            echo "========== Iteration: $i for file: $file most recent computer added $c ==========" >> $clientLogFile
            for computer in ${loop_computers[@]}; do
                echo "Executing commands for host: $computer"

                # Kill any existing process for the user on the remote host
                command0="sshpass -p '$password' ssh $sshopts $login@$computer pkill -u $login"
                echo "Executing: ${command0[*]}"
                eval $command0

                # Remove existing remote folder and create a new one
                command1="sshpass -p '$password' ssh $sshopts $login@$computer 'rm -rf $remoteFolder; mkdir $remoteFolder'"
                echo "Executing: ${command1[*]}"
                eval $command1

                # Copy the JAR file to the remote host's directory
                command2="sshpass -p '$password' scp $sshopts myftpserver/target/$fileName$fileExtension $login@$computer:$remoteFolder$fileName$fileExtension"
                echo "Executing: ${command2[*]}"
                eval $command2

                # Execute the JAR file on the remote host
                command3="sshpass -p '$password' ssh $sshopts $login@$computer 'cd $remoteFolder; java -jar $fileName$fileExtension'"
                echo "Executing: ${command3[*]}"
                eval $command3 >> $serverLogFile &

                # Capture the process ID of the SSH command
                pids+=($!)
                echo
            done
            cd myftpclient
            # Execute the JAR file on the local host
            sleep 5
            command4="java -jar target/$clientFileName$clientFileExtension $file"
            echo "Executing: ${command4[*]}" 
            eval $command4 >> "../$clientLogFile"
            cd ..

            # Wait for all SSH processes to finish
            for pid in "${pids[@]}"; do
                wait $pid
            done
            pid=()
            sleep 5
            echo "============================== End of iteration =============================="
            echo "============================== End of iteration ==============================" >> $clientLogFile
            echo "============================== End of iteration ==============================" >> $serverLogFile
            echo
            echo "" >> $clientLogFile
            echo "" >> $serverLogFile
            echo "" >> $clientLogFile
            echo "" >> $serverLogFile
        done
    done
done


cleanup() {
    for c in ${computers[@]}; do
        eval "sshpass -p '$password' ssh $sshopts $login@$c pkill -u $login"
    done
    echo "Terminated all Java processes."
}



# Call cleanup after all processes are done
cleanup

echo "Server logs are available in the '$serverLogFile' and '$clientLogFile' files"
