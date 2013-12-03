package filecopy;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

/**
 * Diese Klasse ist zum Verschicken von Paketen an den Server (UDP)
 */
public class SendPacket extends Thread {
	
	//**************************** ATTRIBUTE **********************************
	private final String SERVER_NAME;
	private final int SERVER_PORT;
	private DatagramSocket clientSocket; // UDP-Socketklasse
	private InetAddress serverIpAddress; // IP-Adresse des Zielservers
	private byte[] sendData;
	
    //*************************** KONSTRUKTOR *********************************
	public SendPacket(final String SERVER_NAME, final int SERVER_PORT, FCpacket packet) {
		this.SERVER_NAME = SERVER_NAME;
		this.SERVER_PORT = SERVER_PORT;
		this.sendData = packet.getData();

		// UTF-8 Konvertierter String als byte[] --> sendString.getBytes("UTF-8");
	}
	
    //************************** PUBLIC METHODEN ******************************
	@Override
	public void run() {
		try {
			//UDP Socket
			clientSocket = new DatagramSocket();
			
			System.out.println("DATEN: " + sendData + "\nLänge: " + sendData.length + "\nServername: " + InetAddress.getByName(SERVER_NAME) + "\nPORT: " + SERVER_PORT);
			
			//Paket erstellen
			DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, InetAddress.getByName(SERVER_NAME), SERVER_PORT);

			//Paket abschicken
			clientSocket.send(sendPacket);
			
			clientSocket.close();
		} catch (SocketException e) {
			e.printStackTrace();
		} catch (UnknownHostException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
    //*********************** PRIVATE METHODEN ********************************
    /**
     * Sendet eine Nachricht an den Server
     * @param String sendString - erwartet die zu sendende Nachricht
     * @throws IOException 
     */
    private void writeToServer(FCpacket packet) throws IOException {
//        /* Sende den String als UDP-Paket zum Server */
//
//        /* String in Byte-Array umwandeln */
//        byte[] sendData = sendString.getBytes();
//
//        /* Paket erzeugen */
//        DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length,
//                serverIpAddress, SERVER_PORT);
//        /* Senden des Pakets */
//        clientSocket.send(sendPacket);
//
//        System.out.println("UDP Client has sent the message: " + sendString);
    }
}
