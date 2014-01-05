package filecopy;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;

/**
 * Diese Klasse ist dazu da, die Acknowledgements vom Server zu erhalten
 * TODO: Muss terminiert werden, sobald alle ACK´S erhalten wurden
 * Habe eine schnelle Lösung dafür implementiert, jetzt wird terminiert
 */
public class ReceiveAcknowledgement extends Thread {
    //**************************** ATTRIBUTE **********************************
	private final int UDP_PACKET_SIZE = 8;    
    private FileCopyClient fileCopyClient;
    private DatagramSocket clientSocket;
    private double realFileSize;
    private int estimatedAcks;
    
    //*************************** KONSTRUKTOR *********************************
    public ReceiveAcknowledgement(DatagramSocket clientSocket, FileCopyClient fileCopyClient, double fileSize) {
    	this.clientSocket = clientSocket;
    	this.fileCopyClient = fileCopyClient;
    	this.realFileSize = fileSize;
    }
   
    //************************** PUBLIC METHODEN ******************************
    @Override
    public void run() {
        try {
        	//Anzahl an Acks berechnen die eintreffen sollen
        	double fileSizeRounded = Math.round((realFileSize / 1000));
        	int fileSizeInInt = (int) (realFileSize / 1000);
        	
        	if((int) fileSizeRounded == fileSizeInInt) {
        		// +2, da Paket nummer 0 einbezogen werden muss und das letzte Paket mit nicht ganzen 1000 Bytes
        		estimatedAcks = (int) fileSizeRounded + 2;
        	} else {
        		// +1, da Paket nummer 0 mit einbezogen werden muss
        		estimatedAcks = (int) fileSizeRounded + 1;
        	}
        	
        	//Laufe solange, bis für die Anzahl an Pakete, Acks eingetroffen sind
        	while (estimatedAcks > 0) {
            	//Empfang eines Paketes warten
                DatagramPacket receivedPacket = receivePacket();
                
                //Sequenznummer aus erhaltenem Paket filtern
                long receivedSeqNum = getSeqNumFromDatagramPacket(receivedPacket);
                
                //Window (sendePuffer) für FileCopyClient aktualisieren --> notify
                fileCopyClient.acknowledgedPacket(receivedSeqNum);
                
                //Zähler dekrementieren
                estimatedAcks--;
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
        clientSocket.receive(receivePacket);
        
        return receivePacket;
    }
}
