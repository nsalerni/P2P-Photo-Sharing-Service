import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Scanner;
import java.nio.charset.Charset;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.io.*;

/**
 * Server class handles the client's server side (file transfers, etc.).
 */
public class PeerServer {
	final int statusCode200 = 200; // OK
	final int statusCode400 = 400; // Bad Request
	final int statusCode404 = 404; // Not Found
	final int statusCode505 = 505; // HTTP Version Not Supported

	int initialPort; // Initial port of the peer server.
	ServerSocket peerServerTCPSocket; // Peer server TCP socket.
	public static ArrayList<UniqueTCP> peerClientUniqueList = new ArrayList<UniqueTCP>();
	Thread peerServerThread; // Main peer server thread.

	/**
	 * Main constructor of the peer server.
	 * @param port	The port number of the peer server.
	 */
	public PeerServer(int port) {
		initialPort = port;
		try {
			// Create the TCP socket for the peer server.
			peerServerTCPSocket = new ServerSocket(initialPort);
			peerServerThread = new Thread(mainTCPRunnable);
			peerServerThread.start(); // Start the main peer server thread.
		} 
		catch (IOException e) {
			System.out.println("Port Not Available");
		}
	}

	// Run the main thread.
	Runnable mainTCPRunnable = new Runnable() {
		public void run() {
			while (true) {
				String message;
				
				try {
					Socket getFromClient = peerServerTCPSocket.accept(); // Socket to accept information from the client.
					DataInputStream in = new DataInputStream(getFromClient.getInputStream());
					message = in.readUTF();
					Scanner scan = new Scanner(message);
					scan.next();

					// Set the port to an available UDP port.
					int port = findAvaliableUDPPort();
					peerClientUniqueList.add(new UniqueTCP(port));
					message = statusCode200 + " " + port;

					DataOutputStream out = new DataOutputStream(getFromClient.getOutputStream());
					out.writeUTF(message);
					getFromClient.close(); // Close the connection.
				} 
				catch (IOException e) {
					System.out.println("Error In Connecting To Main Socket");
				}
			}
		}
	};

	/**
	 * Find an available UDP port
	 * @return port		The number of the available UDP port.
	 */
	public int findAvaliableUDPPort() {
		int port = 0; // Initially start at 0.
		int portFind = initialPort;
		boolean done = false;
		while (done == false) {

			// Try each port from 0, if the port is available then return it. If not, then increment the port number.
			try {
				ServerSocket tryPort = new ServerSocket(portFind);
				done = true;
				tryPort.close();
				break;
			} 
			catch (Exception e) {
				portFind++;
			}
		}
		port = portFind;
		return port;
	}
	
	public class UniqueTCP {
		final int statusCode200 = 200; // OK
		final int statusCode400 = 400; // Bad Request
		final int statusCode404 = 404; // Not Found
		final int statusCode505 = 505; // HTTP Version Not Supported

		ServerSocket uniqueTCPSocket; // Peer server TCP socket.	
		Thread TCPRunnableThread;	  // Main TCP thread of the peer server's.

		/**
		 * Main constructor of class UniqueTCP.
		 * @param port	The port number.
		 */
		public UniqueTCP(int port) {
			try {
				uniqueTCPSocket = new ServerSocket(port); // Create a new TCP socket.
				TCPRunnableThread = new Thread(TCPRunnable);
				TCPRunnableThread.start(); // Start the main thread.
			} 
			catch (IOException e) {
				System.out.println("Port Not Available");
			}
		}

		// Run the main thread.
		Runnable TCPRunnable = new Runnable() {
			public void run() {
				try {
					byte[] finalBytesArray = null;
					String message = ""; 		// The message.
					String fileName = ""; 		// The name of the file.
					String request = ""; 		// The request message.
					String httpVersion = ""; 	// The HTTP version
					String HTTPResponce = ""; 	// The HTTP response message.
					String connection = "";   	// The type of connection.
					String contentType = "";	// The file type.
					String timeString = getCurrentTime();	// The current time.
					Socket socket = uniqueTCPSocket.accept();
					
					DataInputStream in = new DataInputStream(socket.getInputStream());
					message = in.readUTF();

					Scanner scan = new Scanner(message);
					request = scan.next();
					
					// If the request is "GET".
					if (request.equals("GET")) {
						fileName = scan.next();
						httpVersion = scan.next(); 
						connection = "Close";
						contentType = "image/jpeg";
						
						// If the HTTP version is 1.1, then
						if (httpVersion.equals("HTTP/1.1")) {
							fileName = fileName.substring(1); // Remove the '/'.
							File f = new File(fileName);
							try {
								String newFileName = "";
								String newFileName2 = "";
								newFileName = fileName.substring(0, fileName.indexOf(".jpeg"));
								newFileName2 = fileName.substring(0, fileName.indexOf(".jpeg"));
								newFileName += "---Trial---.jpeg";
								File isFileNameBad = new File(newFileName);
								isFileNameBad.createNewFile();
								isFileNameBad.delete();
								File f2 = new File(newFileName2 + ".jpg");
								
								if (f2.exists())
									f = new File(newFileName2 + ".jpg");
								
								// HTTP 200 OK.
								if (f.exists()) {
									double fileSizeBytes = f.length();
									String lastMod = getFileModifiedTime(f);
									HTTPResponce = createHTTPResponce(statusCode200, timeString, lastMod, "bytes", Integer.toString((int) fileSizeBytes), connection, contentType);
									byte[] httpToBytes = HTTPResponce.getBytes(Charset.forName("UTF-8"));
									FileInputStream fileInputStream = null;
									byte[] fileToBytes = new byte[(int) f.length()];
									
									fileInputStream = new FileInputStream(f);
									fileInputStream.read(fileToBytes);
									fileInputStream.close();

									finalBytesArray = new byte[httpToBytes.length + fileToBytes.length];
									System.arraycopy(httpToBytes, 0, finalBytesArray, 0, httpToBytes.length);
									System.arraycopy(fileToBytes, 0, finalBytesArray, httpToBytes.length, fileToBytes.length);
								} 
								
								// 404 content not found.
								else {
									HTTPResponce = createHTTPResponce(statusCode404, timeString, null, null, null, connection, null);
									finalBytesArray = HTTPResponce.getBytes(Charset.forName("UTF-8"));
								}
							} 
							
							// HTTP 400 Bad Request.
							catch (Exception e) {
								HTTPResponce = createHTTPResponce(statusCode400, timeString, null, null, null, connection, null);
								finalBytesArray = HTTPResponce.getBytes(Charset.forName("UTF-8"));
							}
						} 
						
						// HTTP 505 Wrong Version.
						else {
							HTTPResponce = createHTTPResponce(statusCode505, timeString, null, null, null, connection, null);
							finalBytesArray = HTTPResponce.getBytes(Charset.forName("UTF-8"));
						}
					}

					OutputStream out = socket.getOutputStream();
					DataOutputStream dos = new DataOutputStream(out);
					dos.writeInt(finalBytesArray.length);
					dos.write(finalBytesArray, 0, finalBytesArray.length);
					socket.close();
					uniqueTCPSocket.close();

					for (int i = 0; i < PeerServer.peerClientUniqueList.size(); i++) {
						if (PeerServer.peerClientUniqueList.get(i).equals(this)) {
							PeerServer.peerClientUniqueList.remove(i);
							TCPRunnableThread.stop();
							break;
						}
					}
				} 
				catch (Exception e) {
					// Catch any exception.
				}
			}
		};

		/**
		 * Get the current time as a string
		 * @return timeString	The current time as a string in the format 'yyyy HH:mm:ss'.
		 */
		public String getCurrentTime() {
			Date date = new Date();
			Scanner scan = new Scanner(date.toString());
			String dayName = scan.next();
			String month = scan.next();
			String dateNumber = scan.next();
			DateFormat timeFormat = new SimpleDateFormat("yyyy HH:mm:ss");
			Date time = new Date();
			String timeString = dayName + ", " + dateNumber + " " + month + " " + timeFormat.format(time) + " GMT";
			return timeString;
		}

		/**
		 * Get the time at which the file was last modified.
		 * @param f		The file.
		 * @return timeString	The time the file was last modified.
		 */
		public String getFileModifiedTime(File f) {
			Date date = new Date(f.lastModified());
			Scanner scan = new Scanner(date.toString());
			String dayName = scan.next();
			String month = scan.next();
			String dateNumber = scan.next();
			DateFormat timeFormat = new SimpleDateFormat("yyyy HH:mm:ss");
			Date time = new Date(f.lastModified());
			String timeString = dayName + ", " + dateNumber + " " + month + " " + timeFormat.format(time) + " GMT";
			return timeString;
		}

		/**
		 * Create an HTTP response message.
		 * @param code			The code of the response (200, 400, 404, 505, ...).
		 * @param currDate		The current date.
		 * @param fileModDate	The date the file was last modified at.
		 * @param acceptRange	The accept range.
		 * @param length		The length of the content.
		 * @param connection	The connection type.
		 * @param contentType	The content type.
		 * @return rep			The entire HTTP response message.
		 */
		public String createHTTPResponce(int code, String currDate, String fileModDate, String acceptRange, String length, String connection, String contentType) {
			String rep = "";
			
			// If the response code is 200 OK.
			if (code == statusCode200) {
				rep += "HTTP/1.1 " + code + " " + "OK\r\n";
				rep += "Connection: " + connection + "\r\n";
				rep += "Date: " + currDate + "\r\n";
				rep += "Last-Modified: " + fileModDate + "\r\n";
				rep += "Accept-Ranges: " + acceptRange + "\r\n";
				rep += "Content-Length: " + length + "\r\n";
				rep += "Content-Type: " + contentType + "\r\n\r\n";
			} 
			else {
				// If the response code is 400 Bad Request.
				if (code == statusCode400) {
					rep += "HTTP/1.1 " + code + " " + "Bad Request\r\n";
				} 
				// If the response code is 404 Not Found.
				else if (code == statusCode404) {
					rep += "HTTP/1.1 " + code + " " + "Not Found\r\n";
				} 
				// If the response code is 505 HTTP Version Not Supported.
				else if (code == statusCode505) {
					rep += "HTTP/1.1 " + code + " "
							+ "HTTP Version Not Supported\r\n";
				}
				rep += "Connection: " + connection + "\r\n";
				rep += "Date: " + currDate + "\r\n\r\n";
			}
			return rep;
		}
	}
}
