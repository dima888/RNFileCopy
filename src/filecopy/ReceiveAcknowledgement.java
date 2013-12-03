package filecopy;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class ReceiveAcknowledgement extends Thread {
    //**************************** ATTRIBUTE **********************************
    private final int SERVER_PORT = 50_001;
    private final int BUFFER_SIZE = 1024;
    private DatagramSocket serverSocket; // UDP-Socketklasse
    private InetAddress receivedIPAddress; // IP-Adresse des Clients
    private int receivedPort; // Port auf dem Client
    
    //*************************** KONSTRUKTOR *********************************
    
    
    //************************** PUBLIC METHODEN ******************************
    @Override
    public void run() {
    	
    }
    
    //*********************** PRIVATE METHODEN ********************************
    /**
     * Zum Lesen der Antworten auf Anfragen vom Server
     * @return String - Antwort des Servers
     * @throws IOException 
     */
    private String readFromClient() throws IOException {
//        /* Liefere den nächsten String vom Server */
//        String receiveString = "";
//        
//        /* Paket für den Empfang erzeugen */
//        byte[] receiveData = new byte[BUFFER_SIZE];
//        DatagramPacket receivePacket = new DatagramPacket(receiveData, BUFFER_SIZE);
//
//        /* Warte auf Empfang eines Pakets auf dem eigenen Server-Port */
//        serverSocket.receive(receivePacket);
//
//        /* Paket erhalten --> auspacken und analysieren */
//        receiveString = new String(receivePacket.getData(), 0,
//                receivePacket.getLength());
//        receivedIPAddress = receivePacket.getAddress();
//        receivedPort = receivePacket.getPort();
//
//        System.out.println("UDP Server got from Client: " + receiveString);
//
//        return receiveString;
    }
}
