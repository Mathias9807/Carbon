package carbon;

import java.io.IOException;
import java.net.*;
import java.nio.charset.Charset;
import java.util.Arrays;

import static carbonserver.CarbonServer.*;

public class CarbonClient {
	
	public static final int CLIENT_PORT = 16513;
	public static final int PACKET_HEADER_SIZE = 8;
	
	public static CarbonClient client;
	
	private DatagramSocket 	socket;
	private InetAddress		connectedIP;
	

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
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public void openSocket() throws SocketException {
		socket = new DatagramSocket(CLIENT_PORT);
		System.out.println("CLIENT: Opened socket");
	}
	
	public boolean connectToServer(InetAddress ip) throws IOException {
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
	
	private void sendPacket(String label, byte[] data) {
		if (data != null && data.length + PACKET_HEADER_SIZE > SERVER_PACKET_MAX_SIZE) {
			System.err.println("Couldn't send packet, too much data. ");
			return;
		}

		byte[] header = addArrays(label.getBytes(Charset.forName("UTF-8")), connectedIP.getAddress());
		
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
	
	public static byte[] addArrays(byte[] arr0, byte[] arr1) {
		byte[] r = new byte[arr0.length + arr1.length];
		System.arraycopy(arr0, 0, r, 0, arr0.length);
		System.arraycopy(arr1, 0, r, arr0.length, arr1.length);
		return r;
	}

}
