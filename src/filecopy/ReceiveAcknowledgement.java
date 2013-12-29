package filecopy;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;

/**
 * Diese Klasse ist dazu da, die Acknowledgements vom Server zu erhalten
 * TODO: Muss terminiert werden, sobald alle ACK�S erhalten wurden
 */
public class ReceiveAcknowledgement extends Thread {
    //**************************** ATTRIBUTE **********************************
	private final int UDP_PACKET_SIZE = 8;    
    private FileCopyClient fileCopyClient;
    private DatagramSocket clientSocket;
    
    //*************************** KONSTRUKTOR *********************************
    public ReceiveAcknowledgement(DatagramSocket clientSocket, FileCopyClient fileCopyClient) {
    	this.clientSocket = clientSocket;
    	this.fileCopyClient = fileCopyClient;
    }
   
    //************************** PUBLIC METHODEN ******************************
    @Override
    public void run() {
        try {
        	while (true) {
            	//Empfang eines Paketes warten
                DatagramPacket receivedPacket = receivePacket();
                
                //Sequenznummer aus erhaltenem Paket filtern
                long receivedSeqNum = getSeqNumFromDatagramPacket(receivedPacket);
                
                //Window (sendePuffer) f�r FileCopyClient aktualisieren --> notify
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
     * @return long receivedSeqNum - gibt die ermittelte SequenzNummer zur�ck
     */
    private long getSeqNumFromDatagramPacket(DatagramPacket packet) {
        /* Neues FCpacket erstellen mit inhalt und l�nge des erhaltenen DatagramPackets */
        FCpacket fcReceivePacket = new FCpacket(packet.getData(), packet.getLength());
        
        /* Die im erhaltenen Paket enthaltene SequenzNummer extrahieren */
        long receivedSeqNum = fcReceivePacket.getSeqNum();
        
        return receivedSeqNum;
    }
    
    /**
     * Zum Lesen der Antworten auf Anfragen vom Server
     * @return String - Antwort des Servers
     * @throws IOException 
     */
    private DatagramPacket receivePacket() throws IOException {        
        /* Paket f�r den Empfang erzeugen */
        byte[] receiveData = new byte[UDP_PACKET_SIZE];
        DatagramPacket receivePacket = new DatagramPacket(receiveData, UDP_PACKET_SIZE);
        
        /* Warte auf Empfang eines Pakets auf dem eigenen Server-Port */
        clientSocket.receive(receivePacket);
        
        return receivePacket;
    }
}
