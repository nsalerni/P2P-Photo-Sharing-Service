import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Scanner;

/**
 *  Directory Server class creates a new directory server with four arguments.
 */
public class DirectoryServer {
	public static void main(String[] args) {
		int port = Integer.parseInt(args[0]);	       // The server's main port. The client will connect to this port using UDP. The predecessor server will also connect to this port using TCP.
		int serverID = Integer.parseInt(args[1]);	   // The server's ID, which can be a number from 1 to n depending on the number of servers.
		int sucessorPort = Integer.parseInt(args[2]);  // The port which is used in the TCP connection to the next server in the DHT.
		String sucessorIP = args[3];				   // The IP address of the next server in the DHT.
		Server server = new Server(port, serverID, sucessorPort, sucessorIP);
	}
	
	public static class Server {
		final int statusCode200 = 200; // OK
		final int statusCode400 = 400; // Bad Request
		final int statusCode404 = 404; // Not Found
		final int statusCode505 = 505; // HTTP Version Not Supported

		int initialPort; 			// Initial port to check UDP port availability
		int myPortNumber; 			// First active port number of this server
		int myServerID; 			// Server ID of this server
		String myIP;				// IP of this server
		int mySuccessorPortNumber; 	// Successor server's first active port number
		String mySuccessorIP; 		// Successor server's IP
		int mySuccessorServerID; 	// Successor server's server ID

		Thread mainTCPThread; // Main TCP Thread
		Thread mainUDPThread; // Main UDP Thread

		ServerSocket serverTCPSocket; 	// Main TCP socket, listen for predecessor
		DatagramSocket serverUDPSocket; // Main UDP socket, listen for new client to connect
		
		// Create new unique UDP class for each client
		ArrayList<UniqueUDP> clientUniqueUDPList = new ArrayList<UniqueUDP>(); 
		
		// Hash content list, Key = file name, Value = IP Address
		public static Hashtable<String, String> contentList = new Hashtable<String, String>(); 

		public Server(int port, int serverID, int successorPortNumber, String successorIP) {
			// --- Initialize port numbers, ID's and IP's --- //
			this.myPortNumber = port;
			this.myServerID = serverID;
			this.mySuccessorPortNumber = successorPortNumber;
			this.mySuccessorIP = successorIP;
			this.mySuccessorServerID = myServerID == 4 ? 1 : myServerID + 1;
			this.initialPort = port + 1;
			
			try {
				// Get your own IP address.
				this.myIP = InetAddress.getLocalHost().getHostName();
			} 
			catch (Exception error) {
				System.out.println("You don't have an IP...");
			}

			System.out.println("Server Starting...");
			System.out.println("	-IP: " + myIP + "\n	-Port: " + myPortNumber
					+ "\n	-SeverID: " + myServerID + "\n	-Successor's Port: "
					+ mySuccessorPortNumber + "\n	-Successor's IP: "
					+ mySuccessorIP + "\n	-Successor's Server ID: "
					+ mySuccessorServerID);
			try {
				serverTCPSocket = new ServerSocket(myPortNumber);
				if (serverID == 1)
					serverUDPSocket = new DatagramSocket(myPortNumber);

				mainTCPThread = new Thread(mainTCPRunnable);
				mainUDPThread = new Thread(mainUDPRunnable);
				mainTCPThread.start(); // Start the main TCP thread.
				mainUDPThread.start(); // Start the main UDP thread.
			} 
			catch (Exception error) {
				System.out.println("Port not avaliable.");
			}
		}

		// Run the main TCP thread.
		Runnable mainTCPRunnable = new Runnable() {
			public void run() {
				System.out.println("Main TCP Thread Starting...");
				while (true) {
					String message;
					try {
						Socket getFromPredecessor = serverTCPSocket.accept();
						DataInputStream in = new DataInputStream(getFromPredecessor.getInputStream());
						message = in.readUTF();
						System.out.println("FROM PREDECESSOR SERVER -> " + message);
						
						// If the server ID is "1" and the message is "GET ALL IP", then send the message HTTP 200 OK to the client along with the information.
						if (myServerID == 1 && message.contains("GET ALL IP")) {
							String[] information = init(message);
							String newMessage = statusCode200 + " " + message + " Padding";
							System.out.println("TO CLIENT -> " + newMessage + "\n");
							sendDataToClient(newMessage, information[0], Integer.parseInt(information[1]));
						} 
						
						// If the message is "GET ALL IP", send the data to the successor server.
						else if (message.contains("GET ALL IP")) {
							String[] information = init(message);
							int uniquePort;
							uniquePort = findAvaliableUDPPort();
							clientUniqueUDPList.add(new UniqueUDP(information[0], uniquePort, mySuccessorIP, mySuccessorPortNumber));

							message += " " + myIP + " " + uniquePort;
							System.out.println("TO SUCCESSOR -> " + message + "\n");
							sendToSuccessorServer(message);
						} 
						
						// If the server ID is "1" and the message is "EXIT", then send the message to the client with HTTP code.
						else if (myServerID == 1 && message.contains("EXIT")) {
							String[] information = exit(message);
							message = statusCode200 + " Padding";
							System.out.println("TO CLIENT -> " + message + "\n");
							sendDataToClient(message, information[0], Integer.parseInt(information[1]));
						} 
						
						// // If the message is "EXIT", send the data to the successor server.
						else if (message.contains("EXIT")) {
							exit(message);
							System.out.println("TO SUCCESSOR -> " + message + "\n");
							sendToSuccessorServer(message);
						}
						getFromPredecessor.close();
					}
					catch (Exception error) {
						// Catch any exception.
					}
				}
			}
		};

		// Run the main UDP thread.
		Runnable mainUDPRunnable = new Runnable() {
			public void run() {
				System.out.println("Main UDP Thread Starting...");
				while (true) {
					String message;
					byte[] receiveData = new byte[1024];
					try {
						DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
						serverUDPSocket.receive(receivePacket); // Receieve packet using UDP socket.
						message = new String(receivePacket.getData());
						System.out.println("FROM CLIENT -> " + message);
						
						// If the message contains "GET ALL IP", then create a new messages with the client's info and send it to the successor server.
						if (message.contains("GET ALL IP")) {
							int uniquePort;
							uniquePort = findAvaliableUDPPort();
							clientUniqueUDPList.add(new UniqueUDP(receivePacket.getAddress().getHostAddress(), uniquePort, mySuccessorIP, mySuccessorPortNumber));
							message = "GET ALL IP " + receivePacket.getPort() + " " + receivePacket.getAddress().getHostAddress() + " " + myIP + " " + uniquePort;
							System.out.println("TO SUCCESSOR SERVER -> " + message);
							sendToSuccessorServer(message);
						}
					} 
					catch (Exception error) {
						// Catch any exception.
					}
				}
			}
		};

		/**
		 * Find the next available UDP Port number.
		 * @return port: the port number.
		 */
		public int findAvaliableUDPPort() {
			int port = 0;
			int portFind = initialPort;
			boolean done = false;
			while (done == false) {
				
				// Start from port 0 and see if the port is available.
				try {
					DatagramSocket tryPort = new DatagramSocket(portFind);
					done = true;
					tryPort.close();
					break;
				} 
				// If the port is unavailable, increment the port number and repeat.
				catch (SocketException e) {
					portFind++;
				}
			}
			port = portFind;
			return port; // Return the port number.
		}

		/**
		 * Send a String message to the successor server.
		 * @param message	The message to be sent.
		 * @throws UnknownHostException	 If the host does not exist.
		 * @throws IOException			 If there is an exception when trying to write the message.
		 */
		public void sendToSuccessorServer(String message) throws UnknownHostException, IOException {
			Socket connectToSuccessor = new Socket(mySuccessorIP, mySuccessorPortNumber); // connect to successor
			OutputStream outToServer = connectToSuccessor.getOutputStream();
			DataOutputStream out = new DataOutputStream(outToServer);
			out.writeUTF(message);		// Write the message.
			connectToSuccessor.close(); // Clost the connection to the successor server.
		}

		/**
		 * Send a String message to a client.
		 * @param theMessage	The message to be sent.
		 * @param clientIP		The IP address of the client.
		 * @param clientPort	The port of the client.
		 * @throws IOException	If there is any IO exception when sending the message.
		 */
		public void sendDataToClient(String theMessage, String clientIP, int clientPort) throws IOException {
			byte[] sendData = new byte[1024]; // Data to be sent.
			sendData = theMessage.getBytes(); // Data converted into bytes.
			InetAddress ip = InetAddress.getByName(clientIP); // IP address of the client.
			DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, ip, clientPort); // Datagram packet to be sent.
			serverUDPSocket.send(sendPacket); // Sent the packet.
		}

		/**
		 * When the server is contacted by the successor to exit.
		 * @param message
		 * @return the client's IP address and port number contained in the message.
		 */
		public String[] exit(String message) {
			Scanner scan = new Scanner(message);
			scan.next(); // EXIT
			
			String clientIP = scan.next(); 	 // client IP
			int clientPort = scan.nextInt(); // client port
			int port = 0;					 // initial port number
			
			for (int i = 0; i < myServerID; i++) {
				port = scan.nextInt();
			}
			
			for (int i = 0; i < clientUniqueUDPList.size(); i++) {
				if (clientUniqueUDPList.get(i).clientIP.equals(clientIP)
						&& clientUniqueUDPList.get(i).uniquePort == port) {
					clientUniqueUDPList.get(i).kill();
					clientUniqueUDPList.remove(i);
					break;
				}
			}

			Enumeration em = contentList.keys();
			while (em.hasMoreElements()) {
				String key = (String) em.nextElement();
				if (contentList.get(key).equals(clientIP))
					contentList.remove(key);
			}
			return new String[] { clientIP, clientPort + "" };
		}

		/**
		 * When the server is contacted by the successor to initialize.
		 * @param message
		 * @return the client's IP address and port number contained in the message.
		 */
		public String[] init(String message) {
			Scanner scan = new Scanner(message);
			scan.next();
			scan.next();
			scan.next();
			int clientPort = scan.nextInt();
			String clientIP = scan.next();
			return new String[] { clientIP, clientPort + "" };
		}
	}
	
	public static class UniqueUDP {
		final int statusCode200 = 200; // OK
		final int statusCode400 = 400; // Bad Request
		final int statusCode404 = 404; // Not Found
		final int statusCode505 = 505; // HTTP Version Not Supported
		int uniquePort;	 // Unique UDP port number.
		String clientIP; // IP address of the client.
		Thread uniqueThread;  // Unique thread.
		DatagramSocket uniqueUDPSocket; // UDP socket.
		String mySuccessorIP;  // IP Address of the successor server.
		int mySuccessorPortNumber; // Port number of the successor server.

		/**
		 * Constructor for UDP
		 * @param clientIP  			 The IP address of the client.
		 * @param port				     The port number of the client
		 * @param mySuccessorIP 		 The IP address of the successor server.
		 * @param mySuccessorPortNumber  The port number of the successor server.
		 */
		public UniqueUDP(String clientIP, int port, String mySuccessorIP, int mySuccessorPortNumber) {
			this.clientIP = clientIP;
			this.mySuccessorIP = mySuccessorIP;
			this.mySuccessorPortNumber = mySuccessorPortNumber;
			uniquePort = port;
			try {
				// If the port is available, create the UDP socket.
				uniqueUDPSocket = new DatagramSocket(port);
			} 
			catch (SocketException e) {
				System.out.println("Port not avaliable.");
			}
			uniqueThread = new Thread(UniqueRunnable);
			uniqueThread.start();
		}

		// Run main thread.
		Runnable UniqueRunnable = new Runnable() {
			public void run() {
				while (true) {
					String message;
					byte[] receiveData = new byte[1024];
					try {
						DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length); // UDP Packet.
						uniqueUDPSocket.receive(receivePacket);
						message = new String(receivePacket.getData());
						System.out.println("FROM CLIENT -> " + message);
						
						// If the message contains "UPLOAD", then upload the file to the appropriate server.
						if (message.contains("UPLOAD")) {
							Scanner scan = new Scanner(message);
							scan.next(); // UPLOAD
							String fileName = scan.next(); // Get file name in which to upload.
							System.out.println("TO CLIENT -> " + statusCode200 + " Padding" + "\n");
							sendDataToClient(statusCode200 + " Padding", clientIP, receivePacket.getPort()); // Send the data to the client.
							Server.contentList.put(fileName, clientIP); // Update the servers information with the filename and the IP number of the client who has that file.
						} 
						
						// If the message contains "QUERY", then search for the filename entered as user input.
						else if (message.contains("QUERY")) {
							Scanner scan = new Scanner(message);
							scan.next(); // QUERY
							String fileName = scan.next(); // Get filename.

							String ip = Server.contentList.get(fileName);
							// If the IP is not found, output HTTP status code 404 (page/file not found).
							if (ip == null) {
								System.out.println("TO CLIENT -> " + statusCode404 + " Padding" + "\n");
								sendDataToClient(statusCode404 + " Padding", clientIP, receivePacket.getPort());
							} 
							// If the file is found, output the HTTP status code 200 OK.
							else {
								System.out.println("TO CLIENT -> " + statusCode200 + " " + ip + " Padding" + "\n");
								sendDataToClient(statusCode200 + " " + ip + " Padding", clientIP, receivePacket.getPort());
							}
						} 
						
						// If the message contains "EXIT", then exit the client and send message to successor server.
						else if (message.contains("EXIT")) {
							Scanner scan = new Scanner(message);
							message = scan.next() + " " + clientIP + " " + receivePacket.getPort();
							for (int i = 0; i < 4; i++)
								message += " " + scan.next();
							System.out.println("TO SUCCESSOR SERVER -> " + message);
							sendToSuccessorServer(message);
						}
					} 
					catch (Exception error) {
						// Catch any exceptions.
					}
				}
			}
		};

		/**
		 * Close the thread and the UDP socket.
		 */
		public void kill() {
			uniqueThread.stop();
			uniqueUDPSocket.close();
		}

		/**
		 * Send a String message to the successor server
		 * @param message	The message to be sent.
		 * @throws UnknownHostException		If the host is not found.
		 * @throws IOException				If there is a read/write exception.
		 */
		public void sendToSuccessorServer(String message) throws UnknownHostException, IOException {
			Socket connectToSuccessor = new Socket(mySuccessorIP, mySuccessorPortNumber); // connect to successor
			OutputStream outToServer = connectToSuccessor.getOutputStream();
			DataOutputStream out = new DataOutputStream(outToServer);
			out.writeUTF(message); // Write the message.
			connectToSuccessor.close(); // Close the connection to the server.
		}

		/**
		 * Send data to a client
		 * @param theMessage	The message to be sent.
		 * @param clientIP		The IP address of the client.
		 * @param clientPort	The client's port number.
		 * @throws IOException 	If there is a read/write exception.
		 */
		public void sendDataToClient(String theMessage, String clientIP, int clientPort) throws IOException {
			byte[] sendData = new byte[1024];
			sendData = theMessage.getBytes();	// The String message converted into bytes, so it can be sent.
			InetAddress ip = InetAddress.getByName(clientIP);	// Get Inet address of the client, given the IP.
			DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, ip, clientPort); // Create the UDP packet.
			uniqueUDPSocket.send(sendPacket); // Send the UDP packet.
		}
	}
}
