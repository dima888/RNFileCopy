package filecopy;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Arrays;

/**
 * Diese Klasse ist zum Verschicken von Paketen an den Server (UDP)
 */
public class SendPacket extends Thread {
	
	//**************************** ATTRIBUTE **********************************
	private final String SERVER_NAME; //IP
	private final int SERVER_PORT; //PORT
	private DatagramSocket clientSocket; //UDP-Socketklasse
	private FileCopyClient fileCopyClient;
	private FCpacket packet;
	private int delayTimeInMilliSeconds = 1; 
	
    //*************************** KONSTRUKTOR *********************************
	public SendPacket(DatagramSocket clientSocket, FileCopyClient fileCopyClient, final String SERVER_NAME, final int SERVER_PORT, FCpacket packet) {
		this.clientSocket = clientSocket;
		this.fileCopyClient = fileCopyClient;
		this.SERVER_NAME = SERVER_NAME;
		this.SERVER_PORT = SERVER_PORT;
		this.packet = packet;
	}
	
    //************************** PUBLIC METHODEN ******************************
	@Override
	public void run() {
		try {
			//1ms Verzögerungszeit simulieren
			this.sleep(delayTimeInMilliSeconds);
			
			//Puffer für die Sequenznummer in Bytes
			byte[] seqNumByte = packet.getSeqNumBytes();
			
			//Puffer für die zu verschickenden Daten als Bytes
			byte[] sendDataByte = packet.getData();
			
			//Puffer für die kompletten Daten, Sequenznummer und die Daten in Bytes
			byte[] entire = new byte[seqNumByte.length + sendDataByte.length];
			
			
			//Die ersten 8 Stellen des gesammten Byte-Arrays ist für die Sequenznummer reserviert
			for(int i = 0; i < 8; i++) {
				entire[i] = seqNumByte[i];
			}
			
			//Die Daten ab Stelle 8 in das Byte-Array stecken
			for(int i = 8, j = 0; i < sendDataByte.length + 8; i++, j++) {
				entire[i] = sendDataByte[j];
			}
			
			//Paket erstellen
			DatagramPacket sendPacket = new DatagramPacket(entire, entire.length,
					InetAddress.getByName(SERVER_NAME), SERVER_PORT);
			
			//Paket abschicken
			clientSocket.send(sendPacket);
		} catch (SocketException e) {
			e.printStackTrace();
		} catch (UnknownHostException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
}
