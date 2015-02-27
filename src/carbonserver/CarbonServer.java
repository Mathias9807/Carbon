package carbonserver;

import java.io.IOException;
import java.net.*;
import java.nio.charset.Charset;
import java.util.*;

import static carbon.CarbonClient.CLIENT_PORT;
import static carbon.CarbonClient.addArrays;

/**
 * The main server class. Opens a server and waits for clients to connect. 
 * @author Mathias Johansson
 *
 */

public class CarbonServer {
	
	public static final int SERVER_PORT = 16512;
	public static final int SERVER_PACKET_MAX_SIZE = 1024;
	public static final int PACKET_HEADER_SIZE = 8;
	
	/**
	 * List of every client connected. 
	 */
	private static ArrayList<Client> clients = new ArrayList<Client>();
	
	/**
	 * A HashMap containing one functional interface for every type of packet it can use. 
	 */
	private static Map<String, DataHandler> handler;
	
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
			System.out.println("SERVER: Opened socket on " + InetAddress.getLocalHost().getHostAddress() + "::" + SERVER_PORT);
			
			handler = new HashMap<String, DataHandler>();
			loadHandlers();
		} catch (Exception e) {
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
				packet = new DatagramPacket(new byte[SERVER_PACKET_MAX_SIZE], SERVER_PACKET_MAX_SIZE);
				socket.receive(packet);
				byte[] data = packet.getData();
				byte[] body = Arrays.copyOfRange(data, PACKET_HEADER_SIZE, data.length);
				
				HeaderData header = readHeader(Arrays.copyOf(data, PACKET_HEADER_SIZE));
				
				try {
					handler.get(header.label).handle(header, body);
				}catch (NullPointerException e) {
					System.out.println("SERVER: Received unknown packet type");
				}
			} catch (IOException e) {
			}
		}
	}
	
	/**
	 * Reads the header of any received packet. 
	 * The header is 8 bytes long and consists of:
	 * <ul>
	 * 		<li> A 4 byte UTF-8 encoded string of text labeling the message. </li>
	 * 		<li> The 4 bytes long IPv4 address of the sender. </li>
	 * </ul>
	 * 
	 * @param data
	 * @throws UnknownHostException
	 */
	
	private static HeaderData readHeader(byte[] data) throws UnknownHostException {
		String header = new String(data, Charset.forName("UTF-8"));
		
		InetAddress ip = InetAddress.getByAddress(Arrays.copyOfRange(data, 4, 8));
		
		String label = header.substring(0, 4);
		
		return new HeaderData(label, ip);
	}
	
	/**
	 * Adds the data handlers to the list. 
	 * 
	 * There is one data handler for every type of packet, the header's label 
	 * tells what type a packet's data is. 
	 */
	
	private static void loadHandlers() {
		handler.put("CONN", (header, data) -> {
			handleConnectionRequest(header);
		});
		
		handler.put("PRNT", (header, data) -> {
			String text = new String(data, Charset.forName("UTF-8")).trim();
			if (text.length() > 0) 
				System.out.println("SERVER: \"" + text + "\"");
		});
		
		handler.put("DSCN", (header, data) -> {
			for (int i = 0; i < clients.size(); i++) {
				if (clients.get(i).getIP().equals(header.ip)) {
					System.out.println("SERVER: " + header.ip.getHostAddress() + "::" + CLIENT_PORT + " disconnected");
					clients.remove(i);
				}
			}
		});
	}
	
	/**
	 * Handles packets with a label of "CONN". Adds a new Client object to the list of clients. 
	 * @param header
	 */
	
	private static void handleConnectionRequest(HeaderData header) {
		System.out.println("SERVER: Received connection request from: " + header.ip.getHostAddress() + "::" + CLIENT_PORT);
		
		Client c = new Client(header.ip);
		sendPacket(c, "ACK", null);
		clients.add(c);
	}
	
	/**
	 * Sends a packet of data to Client c. The label specifies the packets header label. 
	 * @param c
	 * @param label
	 * @param data
	 */
	
	private static void sendPacket(Client c, String label, byte[] data) {
		if (data != null && data.length + PACKET_HEADER_SIZE > SERVER_PACKET_MAX_SIZE) {
			System.err.println("Couldn't send packet, too much data. ");
			return;
		}
		
		while (label.length() < 4) 
			label = label + " ";

		byte[] header = addArrays(label.getBytes(Charset.forName("UTF-8")), c.getIP().getAddress());
		
		DatagramPacket packet;
		if (data == null) {
			packet = new DatagramPacket(header, header.length, c.getIP(), CLIENT_PORT);
		}else {
			byte[] completeData = addArrays(header, data);
			packet = new DatagramPacket(completeData, completeData.length, c.getIP(), CLIENT_PORT);
		}
		
		try {
			socket.send(packet);
		} catch (IOException e) {
			e.printStackTrace();
		}
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
