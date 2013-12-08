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
	
    //*************************** KONSTRUKTOR *********************************
	public SendPacket(FileCopyClient fileCopyClient, final String SERVER_NAME, final int SERVER_PORT, FCpacket packet) {
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
			this.sleep(1000);
			
			//UDP Socket
			clientSocket = new DatagramSocket();

			//PAKET erstellen --> erste 8Byte für SeNum und restliche 1000 für DATA
			String sendString = new String(packet.getSeqNumBytes()) + new String(packet.getData());
			//String in ein Byte[] konvertieren UTF-8
			byte[] data = sendString.getBytes("UTF-8");
			
			//Paket erstellen
			DatagramPacket sendPacket = new DatagramPacket(data, data.length, 
					InetAddress.getByName(SERVER_NAME), SERVER_PORT);

			while(true) {
				//Paket abschicken
				clientSocket.send(sendPacket);
			}
//			//Paket abschicken
//			clientSocket.send(sendPacket);
//			
//			//Auf Antwort ACK warten
//			new ReceiveAcknowledgement(fileCopyClient, clientSocket).start();
//			
//			System.out.println("PACKET MIT SEQNUM: " + packet.getSeqNum() 
//			+ " DURCH THREAD: " + Thread.currentThread().getName() + " VERSEMDET");
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
