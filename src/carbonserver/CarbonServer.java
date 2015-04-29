package carbonserver;

import java.io.IOException;
import java.net.*;
import java.nio.charset.Charset;
import java.util.*;

import static carbon.CarbonClient.addArrays;

/**
 * The main server class. Opens a server and waits for clients to connect. 
 * @author Mathias Johansson
 *
 */

public class CarbonServer {
	
	public static final int 	SERVER_PORT = 16512;
	public static final int 	SERVER_PACKET_MAX_SIZE = 1024;
	public static final int 	PACKET_HEADER_SIZE = 12;
	
	/**
	 * How many times per second eventOnUpdate will be executed. Can be set to 0. 
	 */
	public static double 		updatesPerSecond = 1;
	
	/**
	 * List of every client connected. 
	 */
	public static ArrayList<Client> clients = new ArrayList<Client>();
	
	/**
	 * A HashMap containing one functional interface for every type of packet the server can read. 
	 */
	public static Map<String, DataHandler> handler = new HashMap<String, DataHandler>();;
	
	/**
	 * Functional Interface that gets executed as often as updatesPerSecond says. 
	 */
	public static FunctionalClient eventOnUpdate = (c) -> {};
	
	private static DatagramSocket 	socket;
	private static boolean 			running;
	private static boolean 			rebooting;
	
	/**
	 * Decides whether or not the server will read instructions from the systems default InputStream. 
	 */
	public static boolean 			useSystemInputStream = true;

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
		
		if (useSystemInputStream) new Thread(() -> {
			@SuppressWarnings("resource")
			Scanner scan = new Scanner(System.in);
			String input;
			while (true) {
				try {
					input = scan.nextLine();
					
					if (input.equals("exit")) {
						shutdownServer();
						break;
					}
					
					if (input.equals("reboot")) {
						shutdownServer();
						rebooting = true;
						break;
					}
					
					for (int i = 0; i < clients.size(); i++) {
						sendPacket(clients.get(i), "PRNT", ("[Server] " + input).getBytes(Charset.forName("UTF-8")));
					}
					
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}).start();
		
		new Thread(() -> {
			if (updatesPerSecond == 0) return;
			
			while (running) {
				if (eventOnUpdate != null) 
					for (int i = 0; i < clients.size(); i++) {
						Client c = clients.get(i);
						
						eventOnUpdate.execute(c);
					}
				
				try {
					Thread.sleep((long) (1000 / updatesPerSecond));
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}).start();
		
		while (running) {
			try {
				receivePacket();
			} catch (IOException e) {
			}
		}
	}

	/**
	 * Reads the header of any received packet. 
	 * The header is 12 bytes long and consists of:
	 * <ul>
	 * 		<li> A 4 byte UTF-8 encoded string of text labeling the message. </li>
	 * 		<li> The 4 bytes long IPv4 address of the sender. </li>
	 * 		<li> The 4 bytes long port number of the sender. </li>
	 * </ul>
	 * 
	 * @param data
	 * @throws UnknownHostException
	 */
	
	private static HeaderData readHeader(byte[] data) throws UnknownHostException {
		InetAddress ip = InetAddress.getByAddress(Arrays.copyOfRange(data, 4, 8));
		
		int port = 0;
		port += data[8] << 24 & 0xFF000000;
		port += data[9] << 16 & 0xFF0000;
		port += data[10] << 8 & 0xFF00;
		port += data[11] & 0xFF;
		
		String label = new String(Arrays.copyOfRange(data, 0, 4), Charset.forName("UTF-8"));
		
		return new HeaderData(label, ip, port);
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
		
		handler.put("MSSG", (header, data) -> {
			String text = "[" + header.ip.getHostAddress() + "::" + header.port + "] " 
					+ new String(data, Charset.forName("UTF-8")).trim();
			
			for (int i = 0; i < clients.size(); i++) {
				Client c = clients.get(i);
				
				sendPacket(c, "PRNT", text.getBytes(Charset.forName("UTF-8")));
			}
		});
		
		handler.put("DSCN", (header, data) -> {
			handleDisconnect(header);
		});
	}
	
	/**
	 * Handles packets with a label of "CONN". Adds a new Client object to the list of clients. 
	 * @param header
	 */
	
	public static void handleConnectionRequest(HeaderData header) {
		Client c = new Client(header.ip, header.port);
		System.out.println("SERVER: Received connection request from: " + c.getIP().getHostAddress() + "::" + c.getPort());
		
		sendPacket(c, "ACK", null);
		clients.add(c);
	}
	
	public static void handleDisconnect(HeaderData header) {
		for (int i = 0; i < clients.size(); i++) {
			if (clients.get(i).getIP().equals(header.ip)) {
				System.out.println("SERVER: " + header.ip.getHostAddress() + "::" + header.port + " disconnected");
				clients.remove(i);
			}
		}
	}
	
	/**
	 * Receives any incoming packet or blocks until there is one, also executes the proper DataHandler. 
	 * @throws IOException
	 */
	
	private static void receivePacket() throws IOException {
		DatagramPacket packet = new DatagramPacket(new byte[SERVER_PACKET_MAX_SIZE], SERVER_PACKET_MAX_SIZE);
		socket.receive(packet);
		
		byte[] data = packet.getData();
		byte[] body = Arrays.copyOfRange(data, PACKET_HEADER_SIZE, data.length);
		
		HeaderData header = readHeader(Arrays.copyOf(data, PACKET_HEADER_SIZE));
		
		try {
			handler.get(header.label).handle(header, body);
		}catch (NullPointerException e) {
			System.out.println("SERVER: Received unknown packet type: " + header.label);
			e.printStackTrace();
		}
	}
	
	/**
	 * Sends a packet of data to Client c. The label specifies the packets header label. 
	 * 
	 * If the label is shorter than 4 characters it will be padded with white-spaces. 
	 * If it's longer than 4 characters it will be cut off.  
	 * @param c
	 * @param label
	 * @param data
	 */
	
	public static void sendPacket(Client c, String label, byte[] data) {
		if (data != null && data.length + PACKET_HEADER_SIZE > SERVER_PACKET_MAX_SIZE) {
			System.err.println("Couldn't send packet, too much data. ");
			return;
		}
		
		while (label.length() < 4) 
			label = label + " ";
		byte[] labelArray = Arrays.copyOf(label.getBytes(Charset.forName("UTF-8")), 4);
		
		byte[] portArray = new byte[4];
		portArray[0] = (byte) (socket.getLocalPort() >> 24 & 0xFF);
		portArray[1] = (byte) (socket.getLocalPort() >> 16 & 0xFF);
		portArray[2] = (byte) (socket.getLocalPort() >> 8 & 0xFF);
		portArray[3] = (byte) (socket.getLocalPort() & 0xFF);

		byte[] header = addArrays(
				addArrays(labelArray, c.getIP().getAddress()), 
				portArray);
		
		DatagramPacket packet;
		if (data == null) {
			packet = new DatagramPacket(header, header.length, c.getIP(), c.getPort());
		}else {
			byte[] completeData = addArrays(header, data);
			packet = new DatagramPacket(completeData, completeData.length, c.getIP(), c.getPort());
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
	
	public static void shutdownServer() {
		System.out.println("SERVER: Disconnecting clients");
		for (int i = 0; i < clients.size(); i++) {
			Client c = clients.get(i);
			System.out.println("SERVER: Disconnecting: " + c.getIP().getHostAddress());
			sendPacket(c, "DSCN", null);
		}
		
		System.out.println("SERVER: Shutting down server");
		running = false;
		socket.close();
	}

}
