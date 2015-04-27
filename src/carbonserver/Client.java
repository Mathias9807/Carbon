package carbonserver;

import java.net.InetAddress;

public class Client {
	
	public static int 	CONNECTED = 0x01;
	
	private InetAddress ip;
	private int 		port;
	public int 			state;
	
	public Client(InetAddress ia, int p) {
		ip = ia;
		port = p;
	}
	
	public InetAddress getIP() {
		return ip;
	}
	
	public int getPort() {
		return port;
	}
	
}
