#!/bin/bash
source config.txt


# List of hosts file that should contain each host in a separate line
computers=$(cat nodes.txt | xargs -I {} echo {})

# SSH options to skip host key checking
sshopts="-o StrictHostKeyChecking=no"

# Remote folder where files will be transferred
remoteFolder="/tmp/$login/"

echo
# Loop through each computer in the list and perform SSH and SCP operations
for c in ${computers[@]}; do

  echo "Executing commands for host: $c"

  # Kill any existing process for the user on the remote host
  command0="sshpass -p '$password' ssh $sshopts $login@$c pkill -u $login"
  echo "Executing: ${command0[*]}"
  eval $command0

  # Remove existing remote folder
  command1="sshpass -p '$password' ssh $sshopts $login@$c 'rm -rf $remoteFolder'"
  echo "Executing: ${command1[*]}"
  eval $command1
  echo
done

echo "Cleanup completed successfully."