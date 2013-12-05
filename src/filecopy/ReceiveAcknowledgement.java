package filecopy;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;

public class ReceiveAcknowledgement extends Thread {
    //**************************** ATTRIBUTE **********************************
    private final int SERVER_PORT = 23_000;
	private final int UDP_PACKET_SIZE = 8;
    private DatagramSocket serverSocket; // UDP-Socketklasse
    
    private FileCopyClient fileCopyClient;
    
    //*************************** KONSTRUKTOR *********************************
    public ReceiveAcknowledgement(FileCopyClient fileCopyClient) {
    	this.fileCopyClient = fileCopyClient;
    }
   
    //************************** PUBLIC METHODEN ******************************
    @Override
    public void run() {
        try {
            /* UDP-Socket erzeugen (KEIN VERBINDUNGSAUFBAU!)
             * Socket wird an den ServerPort gebunden */
        	//TODO: keine bessere Lösung gefunden
            //serverSocket = new DatagramSocket(SERVER_PORT, InetAddress.getByName("localhost"));
        	serverSocket = new DatagramSocket(SERVER_PORT);
        	
            while (!isInterrupted()) {
            	//Auf Empfang eines Paketes warten
                DatagramPacket receivedPacket = receivePacket();
                
                //Sequenznummer aus erhaltenem Paket filtern
                long receivedSeqNum = getSeqNumFromDatagramPacket(receivedPacket);
                
                System.out.println("RECEIVED ACKNOWLEDGEMENT FOR PACKET SEQNUM: " + receivedSeqNum);
                
                //Window (sendePuffer) für FileCopyClient aktualisieren --> notify
                fileCopyClient.acknowledgedPacket(receivedSeqNum);
            }
        } catch (SocketException ex) {
        	ex.printStackTrace();
        } catch (IOException ex) {
        	ex.printStackTrace();
        }
    }
    
    //*********************** PRIVATE METHODEN ********************************
    /**
     * Ermittelt die SequenzNummer die in einem DatagramPacket mit geschickt wird
     * @param DatagramPacket packet - packet aus welchem die SequenzNummer gefiltert werden soll
     * @return long receivedSeqNum - gibt die ermittelte SequenzNummer zurück
     */
    private long getSeqNumFromDatagramPacket(DatagramPacket packet) {
        /* Neues FCpacket erstellen mit inhalt und länge des erhaltenen DatagramPackets */
        FCpacket fcReceivePacket = new FCpacket(packet.getData(), packet.getLength());
        
        /* Die im erhaltenen Paket enthaltene SequenzNummer extrahieren */
        long receivedSeqNum = fcReceivePacket.getSeqNum();

        System.out.println("FileCopyServer acknowledged packet with seqNum: " + receivedSeqNum);
        
        return receivedSeqNum;
    }
    
    /**
     * Zum Lesen der Antworten auf Anfragen vom Server
     * @return String - Antwort des Servers
     * @throws IOException 
     */
    private DatagramPacket receivePacket() throws IOException {        
        /* Paket für den Empfang erzeugen */
        byte[] receiveData = new byte[UDP_PACKET_SIZE];
        DatagramPacket receivePacket = new DatagramPacket(receiveData, UDP_PACKET_SIZE);

        /* Warte auf Empfang eines Pakets auf dem eigenen Server-Port */
        serverSocket.receive(receivePacket);
        
        return receivePacket;
    }
}
