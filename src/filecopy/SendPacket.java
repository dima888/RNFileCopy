package filecopy;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class SendPacket extends Thread {
	
	//**************************** ATTRIBUTE **********************************
	public final int SERVER_PORT = 50_001;
	public static final int BUFFER_SIZE = 1024;
	private DatagramSocket clientSocket; // UDP-Socketklasse
	private InetAddress serverIpAddress; // IP-Adresse des Zielservers
	
    //*************************** KONSTRUKTOR *********************************
	public SendPacket(FCpacket packet) {
//        clientSocket = new DatagramSocket();
//        //IP wird ermitteln durch den Hostnamen
//        serverIpAddress = InetAddress.getByName(hostname);
//        writeToServer(userName + ": " + message);
//        clientSocket.close();
	}
	
    //************************** PUBLIC METHODEN ******************************
	
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
