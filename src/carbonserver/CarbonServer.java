package carbonserver;

import java.io.IOException;
import java.net.*;
import java.nio.charset.Charset;
import java.util.Arrays;

/**
 * The main server class. Opens a server and waits for clients to connect. 
 * @author Mathias Johansson
 *
 */

public class CarbonServer {
	
	public static final int SERVER_PORT = 16512;
	public static final int SERVER_PACKET_SIZE = 1024;
	
	private static DatagramSocket 	socket;
	private static boolean 			running;
	private static boolean 			rebooting;

	public static void main(String[] args) {
		do {
			rebooting = false;
			
			startServer();
			runServer();
			
		}while (rebooting);
		
		System.exit(0);
	}
	
	/**
	 * Opens a socket and starts the servers components. 
	 */
	
	private static void startServer() {
		System.out.println("SERVER: Starting server");
		try {
			socket = new DatagramSocket(SERVER_PORT);
			System.out.println("SERVER: Opened socket");
		} catch (SocketException e) {
			System.err.println("Failed to open socket on port " + SERVER_PORT + ". "
					+ "Is a server already running? ");
			System.exit(-1);
		}
	}
	
	/**
	 * Starts running the servers main loop. 
	 */
	
	private static void runServer() {
		running = true;
		
		new Thread(() -> {
			byte[] input = new byte[32];
			while (true) {
				try {
					System.in.read(input, 0, input.length);
					if (new String(input).substring(0, 4).equals("exit")) {
						shutdownServer();
						break;
					}
					if (new String(input).substring(0, 6).equals("reboot")) {
						shutdownServer();
						rebooting = true;
						break;
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}).start();
		
		DatagramPacket packet;
		while (running) {
			try {
				packet = new DatagramPacket(new byte[SERVER_PACKET_SIZE], SERVER_PACKET_SIZE);
				socket.receive(packet);
				byte[] data = packet.getData();
				System.out.println(new String(data, Charset.forName("UTF-8")));
				
				HeaderData header = readHeader(Arrays.copyOf(data, 10));
				
				
				readData();
			} catch (IOException e) {
			}
		}
	}
	
	/**
	 * Reads the data of any received packet. 
	 */
	
	private static void readData() {
	}
	
	/**
	 * Reads the header of any received packet. 
	 * The header is 10 bytes long and consists of:
	 * <ul>
	 * 		<li> A 4 byte UTF-8 encoded string of text labeling the message. </li>
	 * 		<li> The 4 bytes long IPv4 address of the sender. </li>
	 * 		<li> The 2 bytes long big-endian port number of the sender. </li>
	 * </ul>
	 * 
	 * @param data
	 * @throws UnknownHostException
	 */
	
	private static HeaderData readHeader(byte[] data) throws UnknownHostException {
		String header = new String(data, Charset.forName("UTF-8"));
		
		InetAddress ip = InetAddress.getByAddress(Arrays.copyOfRange(data, 4, 8));
		
		int port = 0;
		port += data[8] & 0xFF << 8;
		port += data[9] & 0xFF;
		
		String label = header.substring(0, 4);
		if (label.equals("CONN")) 
			System.out.println("Received connection request from: " + ip.getHostAddress() + "::" + port);
		
		return new HeaderData(label, ip, port);
	}
	
	/**
	 * Closes every connection and socket. Allows program to safely exit. 
	 */
	
	private static void shutdownServer() {
		System.out.println("SERVER: Shutting down server");
		running = false;
		socket.close();
	}

}
