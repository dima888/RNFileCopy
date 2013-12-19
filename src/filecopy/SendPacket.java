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
			//1ms Verz�gerungszeit simulieren
			this.sleep(delayTimeInMilliSeconds);

			//PAKET erstellen --> erste 8Byte f�r SeNum und restliche 1000 f�r DATA
			String sendString = new String(packet.getSeqNumBytes()) + new String(packet.getData());
			//String in ein Byte[] konvertieren UTF-8
			byte[] data = sendString.getBytes();
			
			//Paket erstellen
			DatagramPacket sendPacket = new DatagramPacket(data, data.length,
					InetAddress.getByName(SERVER_NAME), SERVER_PORT);
			
			//Paket abschicken
			clientSocket.send(sendPacket);
			
			System.out.println("PACKET MIT SEQNUM: " + packet.getSeqNum() + " UND PACKETGR��E: " + sendPacket.getLength()  
			+ " DURCH THREAD: " + Thread.currentThread().getName() + " VERSENDET");
			if(packet.getSeqNum() == 128 || packet.getSeqNum() == 63) {
				System.out.println(Arrays.toString(packet.getData()));
			}
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
