package carbonserver;

import java.net.InetAddress;

public class Client {
	
	public static int 	CONNECTED = 0x01;
	
	private InetAddress ip;
	public int 			state;
	
	public Client(InetAddress ia) {
		ip = ia;
	}
	
	public InetAddress getIP() {
		return ip;
	}
	
}
