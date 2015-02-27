package carbonserver;

public interface DataHandler {

	public void handle(HeaderData header, byte[] data);

}
