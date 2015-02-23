package carbonserver;

import java.net.InetAddress;

public final class HeaderData {
	
	public String 		label;
	public InetAddress 	ip;
	public int 			port;

	public HeaderData(String l, InetAddress ia, int p) {
		label 	= l;
		ip 		= ia;
		port 	= p;
	}

}
