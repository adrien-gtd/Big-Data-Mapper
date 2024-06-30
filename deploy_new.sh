#!/bin/bash

logFile="logs.txt"
# List of hosts file that should contain each host in a separate line
computers=$(cat nodes.txt | xargs -I {} echo {})
# Password for the hosts 
password="PASSWORD"
# Login username
login="aguittard-22"
# SSH options to skip host key checking
sshopts="-o StrictHostKeyChecking=no"

# Remote folder where files will be transferred
remoteFolder="/tmp/$login/"
# Navigate to the myftpserver directory and build the project
cd myftpserver
echo "Compiling and packaging myftpserver project..."
mvn clean compile assembly:single
cd ..

# Filename and extension of the JAR file to be transferred
fileName="myftpserver-1-jar-with-dependencies"
fileExtension=".jar"

# Remove the logs file if it already exists
rm -f $logFile

echo
# Loop through each computer in the list and perform SSH and SCP operations
for c in ${computers[@]}; do

  echo "Executing commands for host: $c"

  # Kill any existing process for the user on the remote host
  command0="sshpass -p '$password' ssh $sshopts $login@$c pkill -u $login"
  echo "Executing: ${command0[*]}"
  eval $command0

  # Remove existing remote folder and create a new one
  command1="sshpass -p '$password' ssh $sshopts $login@$c 'rm -rf $remoteFolder; mkdir $remoteFolder'"
  echo "Executing: ${command1[*]}"
  eval $command1

  # Copy the JAR file to the remote host's directory
  command2="sshpass -p '$password' scp $sshopts myftpserver/target/$fileName$fileExtension $login@$c:$remoteFolder$fileName$fileExtension"
  echo "Executing: ${command2[*]}"
  eval $command2

  # Execute the JAR file on the remote host
  command3="sshpass -p '$password' ssh $sshopts $login@$c 'cd $remoteFolder; java -jar $fileName$fileExtension'"
  echo "Executing: ${command3[*]}"
  eval $command3 >> $logFile &
  echo
done


cleanup() {
  for c in ${computers[@]}; do
    eval "sshpass -p '$password' ssh $sshopts $login@$c pkill -u $login"
  done
  echo "Terminated all Java processes."
}


# Wait for any key press to terminate Java processes
echo "Server logs are available in the $logFile file"
echo "Press any key to terminate Java processes..."
read -n 1 -s  # Wait for a single character input

cleanup