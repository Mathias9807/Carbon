package carbon;

import java.io.IOException;
import java.net.*;
import java.nio.charset.Charset;
import java.util.*;

import carbonserver.*;
import static carbonserver.CarbonServer.*;

public class CarbonClient {
	
	public static final int PACKET_HEADER_SIZE = 12;

	/**
	 * How many times per second eventOnUpdate will be executed. Can be set to 0. 
	 */
	public static double 	updatesPerSecond = 1;
	
	public static CarbonClient client;
	
	/**
	 * A HashMap containing one functional interface for every type of packet the client can read. 
	 * 
	 * Contains a single handler for 'PRNT' packets by default. More handlers can be added externally. 
	 */
	private static Map<String, DataHandler> handler = new HashMap<String, DataHandler>();
	
	public static Functional eventOnUpdate = () -> {};
	
	public DatagramSocket 	socket;
	public InetAddress		connectedIP;
	
	private Thread 			inputThread, receiveThread, updateThread;
	
	private boolean			running;
	
	/**
	 * Decides whether or not the server will read instructions from the systems default InputStream. 
	 */
	public static boolean 			useSystemInputStream = true;
	

	public static void main(String[] args) {
		if (args.length == 0) {
			System.out.println("Usage: ");
			System.out.println("java -jar [Carbon] [Server IP-address]");
			System.exit(0);
		}
		
		client = new CarbonClient(args[0]);
	}
	
	@SuppressWarnings("resource")
	public CarbonClient(String ipAddress) {
		try {
			connectedIP = InetAddress.getByName(ipAddress);
			
			openSocket();
			boolean connected = connectToServer(connectedIP);
			if (!connected) System.exit(0);

			addHandler("PRNT", (header, data) -> {
				System.out.println("CLIENT: " 
						+ new String(data, Charset.forName("UTF-8")).trim());
			});
			
			addHandler("DSCN", (header, data) -> {
				client.disconnect();
			});
			
			running = true;
			if (useSystemInputStream) inputThread = new Thread(() -> {
				String input = null;
				Scanner s = new Scanner(System.in);
				while (running) {
					try {
						input = s.nextLine();
					}catch (Exception e) { break; }
					
					if (input.equals("exit")) {
						disconnect();
						break;
					}
					
					try {
						sendPacket("MSSG", input.getBytes(Charset.forName("UTF-8")));
					} catch (Exception e) {
						e.printStackTrace();
					}
					
				}
			});
			
			updateThread = new Thread(() -> {
				if (updatesPerSecond == 0) return;
				
				while (running) {
					eventOnUpdate.execute();
					
					try {
						Thread.sleep((long) (1000 / updatesPerSecond));
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			});
			
			receiveThread = new Thread(() -> {
				try {
					while (running) 
						receivePacket();
				}catch (Exception e) {}
			});
			
			if (useSystemInputStream) inputThread.start();
			updateThread.start();
			receiveThread.start();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	private void openSocket() {
		int potentialPort = 16513;
		while (socket == null) {
			DatagramSocket ds;
			try {
				ds = new DatagramSocket(potentialPort);
			} catch (SocketException e) {
				potentialPort++;
				continue;
			}
			socket = ds;
		}
		System.out.println("CLIENT: Opened socket on port: " + socket.getLocalPort());
	}
	
	private boolean connectToServer(InetAddress ip) throws IOException {
		System.out.println("CLIENT: Connecting to server: " + ip.getHostAddress() + "::" + SERVER_PORT);
		
		sendPacket("CONN", null);
		DatagramPacket packet = new DatagramPacket(new byte[PACKET_HEADER_SIZE], PACKET_HEADER_SIZE);
		socket.receive(packet);
		HeaderData header = readHeader(packet.getData());
		
		if (header.label.equals("ACKN")) {
			System.out.println("CLIENT: Connection successful! ");
			return true;
		}
		System.out.println("CLIENT: Connection attempt failed. ");
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
		InetAddress ip = InetAddress.getByAddress(Arrays.copyOfRange(data, 4, 8));
		
		String label = new String(Arrays.copyOfRange(data, 0, 4), Charset.forName("UTF-8"));

		int port = 0;
		port += data[8] << 24 & 0xFF000000;
		port += data[9] << 16 & 0xFF0000;
		port += data[10] << 8 & 0xFF00;
		port += data[11] & 0xFF;
		
		return new HeaderData(label, ip, port);
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
			System.out.println("CLIENT: Received unknown packet type: " + header.label);
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
		
		while (label.length() < 4) 
			label = label + " ";
		byte[] labelArray = Arrays.copyOf(label.getBytes(Charset.forName("UTF-8")), 4);
		
		byte[] portArray = new byte[4];
		portArray[0] = (byte) (socket.getLocalPort() >> 24 & 0xFF);
		portArray[1] = (byte) (socket.getLocalPort() >> 16 & 0xFF);
		portArray[2] = (byte) (socket.getLocalPort() >> 8 & 0xFF);
		portArray[3] = (byte) (socket.getLocalPort() & 0xFF);
		
		byte[] header = addArrays(
				addArrays(
						labelArray, InetAddress.getLocalHost().getAddress()), 
				portArray);
		
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
	
	/**
	 * Adds DataHandler dh to the map of handlers if there is no handler for the key already. 
	 * @param key
	 * @param dh
	 */
	
	public static void addHandler(String key, DataHandler dh) {
		if (handler.containsKey(key)) return;
		
		handler.put(key, dh);
	}
	
	public static Map<String, DataHandler> getHandler() {
		return handler;
	}
	
	public void disconnect() {
		System.out.println("CLIENT: Disconnecting");
		running = false;
		if (useSystemInputStream)
			try {
				System.in.close();
			} catch (IOException e1) {
				e1.printStackTrace();
			}
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
