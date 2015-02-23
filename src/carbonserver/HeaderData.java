package carbonserver;

import java.net.InetAddress;

public final class HeaderData {
	
	public String 		label;
	public InetAddress 	ip;

	public HeaderData(String l, InetAddress ia) {
		label 	= l;
		ip 		= ia;
	}

}
