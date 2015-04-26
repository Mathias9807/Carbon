package carbon;

import java.io.IOException;
import java.net.*;
import java.nio.charset.Charset;
import java.util.*;

import carbonserver.*;
import static carbonserver.CarbonServer.*;

public class CarbonClient {
	
	public static final int CLIENT_PORT = 16513;
	public static final int PACKET_HEADER_SIZE = 8;
	
	public static CarbonClient client;
	
	/**
	 * A HashMap containing one functional interface for every type of packet the client can read. 
	 * 
	 * Contains a single handler for 'PRNT' packets by default. More handlers can be added externally. 
	 */
	public Map<String, DataHandler> handler;
	
	private DatagramSocket 	socket;
	private InetAddress		connectedIP;
	
	private boolean			running;
	

	public static void main(String[] args) {
		if (args.length == 0) {
			System.out.println("Usage: ");
			System.out.println("java -jar [Carbon] [Server IP-address]");
			System.exit(0);
		}
		
		client = new CarbonClient(args[0]);
	}
	
	public CarbonClient(String ipAddress) {
		try {
			connectedIP = InetAddress.getByName(ipAddress);
			
			openSocket();
			connectToServer(connectedIP);

			handler = new HashMap<String, DataHandler>();
			handler.put("PRNT", (header, data) -> {
				System.out.println(new String(data, Charset.forName("UTF-8")).trim());
			});
			
			running = true;
			new Thread(() -> {
				String input;
				@SuppressWarnings("resource")
				Scanner s = new Scanner(System.in);
				while (true) {
					input = s.nextLine();
					
					if (input.equals("exit")) {
						break;
					}
					
					try {
						sendPacket("PRNT", input.getBytes(Charset.forName("UTF-8")));
					} catch (Exception e) {
						e.printStackTrace();
					}
					
				}
				running = false;
				disconnect();
			}).start();
			
			try {
				while (running) 
					receivePacket();
			}catch (Exception e) {}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	private void openSocket() throws SocketException {
		socket = new DatagramSocket(CLIENT_PORT);
		System.out.println("CLIENT: Opened socket");
	}
	
	private boolean connectToServer(InetAddress ip) throws IOException {
		System.out.println("CLIENT: Connecting to server: " + ip.getHostAddress() + "::" + SERVER_PORT);
		
		sendPacket("CONN", null);
		DatagramPacket packet = new DatagramPacket(new byte[PACKET_HEADER_SIZE], PACKET_HEADER_SIZE);
		socket.receive(packet);
		String answer = new String(Arrays.copyOf(packet.getData(), 3));
		
		if (answer.equals("ACK")) {
			System.out.println("CLIENT: Connection successful! ");
			return true;
		}
		return false;
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
	 * Receives any incoming packet or blocks until there is one, also executes the proper DataHandler. 
	 * @throws IOException
	 */
	
	private void receivePacket() throws IOException {
		DatagramPacket packet = new DatagramPacket(new byte[SERVER_PACKET_MAX_SIZE], SERVER_PACKET_MAX_SIZE);
		socket.receive(packet);
		byte[] data = packet.getData();
		byte[] body = Arrays.copyOfRange(data, PACKET_HEADER_SIZE, data.length);
		
		HeaderData header = readHeader(Arrays.copyOf(data, PACKET_HEADER_SIZE));
		
		try {
			handler.get(header.label).handle(header, body);
		}catch (NullPointerException e) {
			System.out.println("CLIENT: Received unknown packet type");
		}
	}
	
	/**
	 * Sends a packet of data to the connected server. 
	 * @param label
	 * @param data
	 * @throws UnknownHostException
	 */
	
	public void sendPacket(String label, byte[] data) throws UnknownHostException {
		if (data != null && data.length + PACKET_HEADER_SIZE > SERVER_PACKET_MAX_SIZE) {
			System.err.println("Couldn't send packet, too much data. ");
			return;
		}

		byte[] header = addArrays(label.getBytes(Charset.forName("UTF-8")), InetAddress.getLocalHost().getAddress());
		
		DatagramPacket packet;
		if (data == null) {
			packet = new DatagramPacket(header, header.length, connectedIP, SERVER_PORT);
		}else {
			byte[] completeData = addArrays(header, data);
			packet = new DatagramPacket(completeData, completeData.length, connectedIP, SERVER_PORT);
		}
		
		try {
			socket.send(packet);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private void disconnect() {
		System.out.println("CLIENT: Disconnecting");
		try {
			sendPacket("DSCN", null);
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
		socket.close();
	}
	
	public static byte[] addArrays(byte[] arr0, byte[] arr1) {
		byte[] r = new byte[arr0.length + arr1.length];
		System.arraycopy(arr0, 0, r, 0, arr0.length);
		System.arraycopy(arr1, 0, r, arr0.length, arr1.length);
		return r;
	}

}
