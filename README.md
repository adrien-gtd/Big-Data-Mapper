### README for Big Data Mapper

# Big Data Mapper

Big Data Mapper is an implementation of the Hadoop MapReduce protocol from scratch, utilizing sockets and FTP for communication and file transfers. Created as part of the SLR207 coursework at Telecom Paris, this project aims to provide a foundational understanding of distributed data processing, demonstrating the key concepts and mechanisms of the MapReduce framework. For performance analysis and conclusions, refer to the Jupyter notebook located in the data_analysis folder. 

## Features

### Core Functionality
- **Asynchronous Socket Communication**: Implemented asynchronous socket servers to enable efficient communication between the master and the worker nodes. This includes proper socket closing and message sending capabilities.
- **FTP-Based Data Distribution**: Utilized FTP for distributing input data to nodes and retrieving results, ensuring reliable and efficient file transfers.

### MapReduce Workflow

- **Input Splitting and Distribution**: The input file is segmented into chunks and distributed among multiple nodes.
- **Mapping Phase**: Each node processes its allocated data chunk, generating key-value pairs for each word.
- **First Shuffle Phase**: Key-value pairs are redistributed to ensure all values associated with the same key reside on the same node.
- **First Reduce Phase**: Nodes consolidate key-value pairs with identical keys, generating intermediate results.
- **Second Shuffle Phase**: Intermediate results are redistributed based on key occurrence frequencies—nodes receive key-value pairs accordingly, facilitating subsequent sorting.
- **Final Reduce and Sort Phase**: Each node organizes its key-value pairs by occurrence. The master node merges these sorted outputs to produce a final, ordered list of words by their frequency.

### Performance Analysis
- **Execution Time Measurement**: Added functionality to measure the execution time of the entire process, aiding in performance evaluation.
- **Synchronization vs Computation Time**: Implemented measurement of synchronization time relative to computation time to analyze the overhead introduced by parallel processing.
- **Speedup Analysis**: Analyzed speedup by varying the number of processes, providing insights into the scalability of the program.

### Automation and Utilities
- **Automated Data Collection**: Created a script to automatically gather performance data, simplifying the analysis process.
- **Log-Based Workload Partitioning**: Ensured a more evenly distributed workload by implementing log-based partitioning of occurrences.
- **CSV Logging**: Saved performance measurements to a CSV file for easy analysis and visualization.
- **Deployment and Cleanup Scripts**: Developed scripts to streamline deployment, secure login information, and clean up generated files post-execution.

## Installation

To install and run Big Data Mapper, follow these steps:

1. Clone the repository:
   ```sh
   git clone https://github.com/adrien-gtd/Big-Data-Mapper.git
   cd Big-Data-Mapper
   ```

2. Set up the environment:
   You need to create a config.txt file containing login infos for the ssh connections. It only need to contain:
   ```
   password="Your password"
   login="Your login"
   ```
   Add all the nodes addresses in the nodes.txt file.

4. Deploy the servers on the remote machines using ssh:
   ```sh
   ./deploy_server.sh
   ```

5. Run the client indicating your input file:
   ```sh
   cd myftpclient/
   clean compile assembly:single
   ./target/myftpclient-1-jar-with-dependencies.jar ../source_file/your_input > ../logs/client_logs.txt
   ```

6. Clean up after execution:
   ```sh
   ./clean.sh
   ```

## Usage

1. **Configure Nodes**: Ensure all nodes are properly configured and accessible via the network.
2. **Input Data**: Place your input data in the designated input directory.
3. **Execution**: Execute the above steps to start the MapReduce process.
4. **Monitoring**: Monitor the logs and performance metrics to analyze the execution.
5. **Results**: Retrieve the results from the output directory in the myftpclient folder and analyze the performance data stored in the CSV file.


Overall, Big Data Mapper successfully demonstrates the core principles of the MapReduce protocol, providing a valuable learning tool for distributed data processing. The project highlights both the benefits and challenges of parallel processing, offering insights into areas for further optimization.

## Author

- Adrien Guittard - [GitHub](https://github.com/adrien-gtd)
