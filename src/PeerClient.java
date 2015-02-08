import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Scanner;

/**
 * Client class handles the client's connections.
 */
public class PeerClient {
	static String server1IP;   // IP Address of the first server in the DHT.
	static int server1Port;    // Port number of the first server in the DHT.
	static int peerServerPort; // Port number of the client's server. This port is always listening for incoming connections from other clients who want a file.

	public static void main(String[] args) {
		Thread mainThread;	// Main thread.
		
		peerServerPort = Integer.parseInt(args[0]); // Port number of the client's server. This port is always listening for incoming connections from other clients who want a file.
		server1IP = args[1];						// IP Address of the first server in the DHT.
		server1Port = Integer.parseInt(args[2]);    // Port number of the first server in the DHT.
		
		mainThread = new Thread(mainRunnable);
		mainThread.start(); // Start the main thread.
	}

	static Runnable mainRunnable = new Runnable() {
		public void run() {
			System.out.println("Client Starting");
			
			Client peerClient = new Client(server1IP, server1Port,peerServerPort); // Create an instance of peerClient.
			PeerServer peerServer = new PeerServer(peerServerPort);						   // Create an instance of peerServer.
			
			Scanner scannerIn = new Scanner(System.in); // Scanner to read user input.
			String userInput; // Holds user input.
			
			while (true) {
				// Display the menu and ask the user to choose an option.
				System.out.println("Your Inputs: U=Upload, Q=Query For Content, E=Exit");
				System.out.print("Enter: ");
				userInput = scannerIn.next(); // Read user input.
				
				// If the user enters "U" (UPLOAD), prompt the user to enter the name of the file they wish to upload.
				if (userInput.equalsIgnoreCase("U")) {
					System.out.print("Enter File Name: ");
					userInput = scannerIn.next();
					
					// Calculate the ID of the server to which the file will be uploaded.
					int calculatedServerID = 0;
					for (int i = 0; i < userInput.length(); i++) {
						calculatedServerID += (int) userInput.charAt(i);
					}
					calculatedServerID = calculatedServerID % 4;
					
					try {
						// Upload the file to the server.
						peerClient.uploadData(calculatedServerID, userInput);
					} 
					catch (Exception e) {
						System.out.println("Could not connect to server.");
					}
				} 
				
				// If the user enters "Q" (QUERY), then ask the user for the filename of the file they are looking for.
				else if (userInput.equalsIgnoreCase("Q")) {
					System.out.print("Enter File Name: ");
					userInput = scannerIn.next();

					// Calcuate the ID of the server where the file is stored.
					int calculatedServerID = 0;
					for (int i = 0; i < userInput.length(); i++) {
						calculatedServerID += (int) userInput.charAt(i);
					}
					calculatedServerID = calculatedServerID % 4;
					
					try {
						// Query the appropriate server.
						peerClient.query(calculatedServerID, userInput);
					} 
					catch (Exception e) {
						System.out.println("Could not connect to server.");
					}
				} 
				
				// The the user enters "E" (EXIT), then exit the client.
				else if (userInput.equalsIgnoreCase("E")) {
					try {
						// Close the current client.
						peerClient.exit();
					} 
					catch (Exception e) {
						System.out.println("Could not connect to server.");
					}
				} 
				
				// If the user enters anything else, tell them it is invalid input.
				else {
					System.out.println("Invalid Input.");
				}
			}
		}
	}; 
	
	public static class Client {
		int peerServerPort;	// Port number of the client's server. This port is always listening for incoming connections from other clients who want a file.
		String[] fileName;	// Name of the file.
		int[] serverPortNumbers = new int[4];	// Open port numbers of all four servers.
		String[] serverIPs = new String[4];		// IPs of all four servers.
		DatagramSocket clientUDPSocket;			// UDP socket.

		/**
		 * Main constructor of class Client.
		 * @param server1IP		 IP Address of the first server in the DHT.
		 * @param server1Port	 Port number of the first server in the DHT.
		 * @param peerServerPort Port number of the client's server. This port is always listening for incoming connections from other clients who want a file.
		 */
		public Client(String server1IP, int server1Port, int peerServerPort) {
			this.peerServerPort = peerServerPort;
			this.serverIPs[0] = server1IP; 
			this.serverPortNumbers[0] = server1Port; 

			try {
				// Create the client's UDP socket and init.
				clientUDPSocket = new DatagramSocket();
				init();
			} 
			catch (Exception e) {
			} // error- port now avaliable
		}

		/**
		 * Initialize the client.
		 * @throws Exception
		 */
		public void init() throws Exception {
			String message;		
			String statusCode;	// HTTP status code.
			sendDataToServer("GET ALL IP", serverIPs[0], serverPortNumbers[0]); // Send the message "GET ALL IP" to server.
			message = receiveDataFromServer();
			Scanner scan = new Scanner(message);
			statusCode = scan.next();
			scan.next();
			scan.next();
			scan.next();
			scan.next();
			scan.next(); // GET ALL, IP myPort, myIP.

			// If the HTTP status code was 200 OK, then the client was initialized to the server.
			if (statusCode.equals("200")) {
				System.out.println("FROM SERVER -> Client Initilized To Server");
			}
			// Build two arrays, one for all the server IPs and one for the server port numbers (of all four DHT servers).
			for (int i = 0; i < 4; i++) {
				serverIPs[i] = scan.next();
				serverPortNumbers[i] = Integer.parseInt(scan.next());
			}
		}

		/**
		 * Upload data from client to server.
		 * @param id		ID number of the server in which to upload data.
		 * @param fileName	Name of the file to uplaod.
		 * @throws Exception
		 */
		public void uploadData(int id, String fileName) throws Exception {
			String statusCode;
			String message = "UPLOAD " + fileName + " " + InetAddress.getLocalHost().getHostAddress() + " Padding";
			sendDataToServer(message, serverIPs[id], serverPortNumbers[id]); // Send message upload to the appropriate server.
			message = receiveDataFromServer();
			Scanner scan = new Scanner(message);
			statusCode = scan.next();
			
			// If the server returned the HTTP status code 200 OK, then the file was successfully added to the DHT.
			if (statusCode.equals("200")) {
				System.out.println("FROM SERVER -> File Added To DHT");
			}
		}

		/**
		 * Remove the client from the peer to peer network.
		 * @throws Exception
		 */
		public void exit() throws Exception {
			byte[] receiveData = new byte[1024];
			String statusCode; // HTTP status code.
			String message = "EXIT " + serverPortNumbers[0] + " " + serverPortNumbers[1] + " " + serverPortNumbers[2] + " " + serverPortNumbers[3] + " Padding";
			sendDataToServer(message, serverIPs[0], serverPortNumbers[0]); // Send message exit to server.
			message = receiveDataFromServer();
			clientUDPSocket.close(); // Close the client's UDP socket.
			Scanner scan = new Scanner(message);
			statusCode = scan.next();
			
			// If the status code returned by the server is 200 OK, then the client has been successfully removed.
			if (statusCode.equals("200")) {
				System.out.println("FROM SERVER -> All contents removed sucessfully");
			}
			System.exit(0); // Quit the application with exit code 0 (success).
		}

		/**
		 * Query for a specific file.
		 * @param id		The ID of the server that contains the file.
		 * @param fileName	The name of the file.
		 * @throws Exception
		 */
		public void query(int id, String fileName) throws Exception {
			String clientToContactIP;
			String statusCode; // HTTP status code.
			String message = "QUERY " + fileName + " Padding";
			sendDataToServer(message, serverIPs[id], serverPortNumbers[id]); // Send the message "QUERY" to the appropriate server.
			message = receiveDataFromServer();
			Scanner scan = new Scanner(message);
			statusCode = scan.next();

			// If the status code is 404, then the file was not found.
			if (statusCode.equals("404")) {
				System.out.println("FROM SERVER -> Content Not Found");
			} 
			
			// If the status code is 200 OK, then the file was found.
			else if (statusCode.equals("200")) {
				System.out.println("FROM SERVER -> Content Found, IP given ");
				scan = new Scanner(message);
				scan.next(); // status code
				clientToContactIP = scan.next(); // The IP of the client who has the file.
				
				// Create the HTTP GET request.
				String HTTPRequest = createHTTPRequest("GET", fileName, "Close", InetAddress.getByName(clientToContactIP).getHostName(), "image/jpeg", "en-us");
				message = connectToPeerServer("OPEN " + fileName, clientToContactIP, peerServerPort); // Connect to the server of the client who has the file.
				scan = new Scanner(message);
				statusCode = scan.next();
				int newPort = scan.nextInt();
				
				// If the status code is 200 OK, then the request was sent successfully.
				if (statusCode.equals("200")) {
					System.out.println("FROM PEER SERVER -> New Connection Open On Port " + newPort);
					System.out.println("--HTTP Request Sent to Server-- START\n" + HTTPRequest + "--HTTP Request Sent to Server--END\n");
					connectToUniqueServer(fileName, HTTPRequest, clientToContactIP, newPort);
				}
			} // Server has sent the ip
		}

		/**
		 * Send data to a given server.
		 * @param message		The message to be sent.
		 * @param serverIP		The IP address of the server.
		 * @param serverPort	The port number of the server.
		 * @throws IOException
		 */
		public void sendDataToServer(String message, String serverIP, int serverPort)
				throws IOException {
			byte[] sendData = new byte[1024];
			sendData = message.getBytes();
			InetAddress internetAddress = InetAddress.getByName(serverIP); // Get the Inet address of the server.
			DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, internetAddress, serverPort); // The packet to be sent to the server.
			clientUDPSocket.send(sendPacket); // Send the packet using UDP.
		}

		/**
		 * Receive data from the server.
		 * @return	The data received from the server.
		 * @throws IOException
		 */
		public String receiveDataFromServer() throws IOException {
			byte[] receiveData = new byte[1024];
			DatagramPacket recievePacket = new DatagramPacket(receiveData, receiveData.length);
			clientUDPSocket.receive(recievePacket);
			return new String(recievePacket.getData());
		}

		/**
		 * Connect to a peer's server.
		 * @param message	The message to be received.
		 * @param ip		The IP of the peer's server.
		 * @param port		The port number of the peer's server.
		 * @return message
		 * @throws UnknownHostException		If the host does not exist.
		 * @throws IOException				If there is a read/write error.
		 */
		public String connectToPeerServer(String message, String ip, int port) throws UnknownHostException, IOException {
			Socket connectToPeerServer = new Socket(ip, port); // connect to peer server.
																
			OutputStream outToServer = connectToPeerServer.getOutputStream();
			DataOutputStream out = new DataOutputStream(outToServer);
			out.writeUTF(message);
			DataInputStream in = new DataInputStream(connectToPeerServer.getInputStream());
			message = in.readUTF();
			connectToPeerServer.close(); // Close the connection to the peer server.
			return message;
		}

		/**
		 * Connect to a specific server.
		 * @param fileName		The name of the file.
		 * @param httpRequest	The HTTP request message.
		 * @param ip			The IP of the server.
		 * @param port			The port number of the server.
		 * @throws UnknownHostException		If the server does not exist.
		 * @throws IOException				If there is a read/write error.
		 */
		public void connectToUniqueServer(String fileName, String httpRequest, String ip, int port) throws UnknownHostException, IOException {
			Socket connectToUniqueServer = new Socket(ip, port); // connect to peer server.
															
			OutputStream outToServer = connectToUniqueServer.getOutputStream();
			DataOutputStream out = new DataOutputStream(outToServer);
			out.writeUTF(httpRequest);

			InputStream in = connectToUniqueServer.getInputStream();
			DataInputStream dis = new DataInputStream(in);
			int len = dis.readInt();
			byte[] data = new byte[len];
			if (len > 0) {
				dis.readFully(data);
			}
			connectToUniqueServer.close();	 // Close the connection to the server.

			String s = new String(data);
			Scanner scan = new Scanner(s);
			String responceStaus = scan.nextLine() + "\r\n";
			String temp;
			
			if (responceStaus.contains("HTTP/1.1 200 OK")) {
				responceStaus = getHTTPResponse(scan, responceStaus);
				File outputfile = new File(fileName + ".jpeg");
				int fileSize = data.length - responceStaus.getBytes().length;
				byte[] backToBytes = new byte[fileSize];
				
				for (int i = responceStaus.getBytes().length; i < data.length; i++) {
					backToBytes[i - responceStaus.getBytes().length] = data[i];
				}

				FileOutputStream fos = new FileOutputStream(outputfile);
				fos.write(backToBytes);
				fos.close(); // Close the fileoutput stream.
			} 
			else if (responceStaus.contains("HTTP/1.1 400 Bad Request")) {
				responceStaus = getHTTPResponse(scan, responceStaus);
			} 
			else if (responceStaus.contains("HTTP/1.1 404 Not Found")) {
				responceStaus = getHTTPResponse(scan, responceStaus);
			} 
			else if (responceStaus.contains("HTTP/1.1 505 HTTP Verson Not Supported")) {
				responceStaus = getHTTPResponse(scan, responceStaus);
			}
			System.out.println("--HTTP Responce Got From Server-- START\n" + responceStaus + "--HTTP Responce Got From Server--END\n");
		}

		/**
		 * Get an HTTP response message
		 * @param scan	Scanner to read the message.
		 * @param rep	The entire message.
		 * @return rep 	The entire HTTP response message.
		 */
		public String getHTTPResponse(Scanner scan, String rep) {
			String temp;
			while (scan.hasNext()) {
				temp = scan.nextLine() + "\r\n";
				rep += temp;
				if (temp.equals("\r\n")) {
					break;
				}
			}
			return rep;
		}

		/**
		 * Create an HTTP request.
		 * @param request		The request to be created
		 * @param object		The name of the file.
		 * @param connection	The type of connection.
		 * @param host			The name of the host.
		 * @param acceptType	The file types to accept.
		 * @param acceptLan		The languages to accept.
		 * @return req			The entire HTTP request message.
		 */
		public String createHTTPRequest(String request, String object, String connection, String host, String acceptType, String acceptLan) {
			String req = "";
			req += request + " /" + object + ".jpeg" + " HTTP/1.1\r\n";
			req += "Host: " + host + "\r\n";
			req += "Connection: " + connection + "\r\n";
			req += "Accept: " + acceptType + "\r\n";
			req += "Accept-Language: " + acceptLan + "\r\n\r\n";
			return req;
		}
	}
}
