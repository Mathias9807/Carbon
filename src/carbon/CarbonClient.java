package carbon;

import java.io.IOException;
import java.net.*;
import java.nio.charset.Charset;

import static carbonserver.CarbonServer.*;

public class CarbonClient {
	
	public static final int CLIENT_PORT = 16513;
	
	public static CarbonClient client;
	
	private DatagramSocket 	socket;
	private InetAddress		connectedIP;
	private int				connectedPort;
	
	private byte[]			headerConnInfo;
	

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
			connectedPort = SERVER_PORT;
			
			openSocket(CLIENT_PORT);
			connectToServer(connectedIP, connectedPort);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public void openSocket(int port) throws SocketException {
		socket = new DatagramSocket(CLIENT_PORT);
		System.out.println("CLIENT: Opened socket");
	}
	
	public void connectToServer(InetAddress ip, int port) throws IOException {
		System.out.println("CLIENT: Connecting to server: " + ip.getHostAddress() + "::" + port);
		
		byte[] portArray = new byte[2];
		portArray[0] = (byte) ((connectedPort & 0xFF00) >> 8);
		portArray[1] = (byte) (connectedPort & 0xFF);
		
		headerConnInfo = addArrays(connectedIP.getAddress(), portArray);
		
		sendPacket("CONN", null);
	}
	
	private void sendPacket(String label, byte[] data) {
		if (data != null && data.length + 10 > SERVER_PACKET_SIZE) {
			System.err.println("Couldn't send packet, too much data. ");
			return;
		}

		byte[] header = addArrays(label.getBytes(Charset.forName("UTF-8")), headerConnInfo);
		
		DatagramPacket packet;
		if (data == null) {
			packet = new DatagramPacket(header, header.length, connectedIP, connectedPort);
		}else {
			byte[] completeData = addArrays(header, data);
			packet = new DatagramPacket(completeData, completeData.length, connectedIP, connectedPort);
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
