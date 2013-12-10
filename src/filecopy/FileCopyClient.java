package filecopy;

/* FileCopyClient.java
 Version 0.1 - Muss ergänzt werden!!
 Praktikum 3 Rechnernetze BAI4 HAW Hamburg
 Autoren:
 */

import java.io.*;
import java.net.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.Semaphore;

import com.sun.swing.internal.plaf.synth.resources.synth;

public class FileCopyClient extends Thread {

	// -------- Constants
	public final static boolean TEST_OUTPUT_MODE = false;

	public final int SERVER_PORT = 23_000;

	public final int UDP_PACKET_SIZE = 1008;
	
	public final int DATA_SIZE = UDP_PACKET_SIZE - 8;

	// -------- Public parms
	public String servername;

	public String sourcePath;

	public String destPath;

	public int windowSize;

	public long serverErrorRate;

	// -------- Variables
	// current default timeout in nanoseconds
	private long timeoutValue = 1_000_000_000l;

	//Sequenznummmer des zu letzt verschickten Paketes --> 1 da 0 für die initialisierung festgelegt ist
	private long nextSeqNum = 0;
	
	//Sequenznummer des ältesten Paketes, für welches noch kein ACK vorliegt --> 1 da 0 für die initialisierung festgelegt ist
	private long sendBase = 1;
	
	private DatagramSocket clientSocket;
	
	//Sende Puffer
	private List<FCpacket> sendBuffer = new ArrayList<>();
	
	//Anzahl freier pufferplätze
	private Semaphore freiePlaetze; 
	
	//Nur ein Thread zurzeit darf auf den sendepuffer zugreifen
	private Semaphore mutex = new Semaphore(1);
	
	//Path Objekt zur Datei
	private Path p;
	
	//Einen Scanner auf der Datei initialisieren
	private Scanner s;

	// Constructor
	public FileCopyClient(String serverArg, String sourcePathArg,
			String destPathArg, String windowSizeArg, String errorRateArg) {
		servername = serverArg;
		sourcePath = sourcePathArg;
		destPath = destPathArg;
		windowSize = Integer.parseInt(windowSizeArg);
		serverErrorRate = Long.parseLong(errorRateArg);
		
		p = Paths.get(sourcePath);
		
		try {
			s = new Scanner(p);
		} catch (Exception e) {
			System.err.println("Datei: " + p.getFileName() + " unter dem Pfad: " + p + " nicht gefunden!");
		}
		freiePlaetze = new Semaphore(windowSize);
	}
	
	//*************************************SELBST IMPLEMENTIERT*********************************************
	public void runFileCopyClient() {
		try {
			clientSocket = new DatagramSocket();
			
			System.out.println("ERSTES SPEZIAL PAKET WIRD VERSCHICKT\n");
			sendFirstPacket();
			
			//durch das Semaphor wird das Windo repräsentiert
			//z.B. windowsize = 3, so können nur 3 pakete los geschickt werden und falls ein Thread versucht ein 4 los
			//zu schicken, so wird er in die Wait-Queue gesteckt und muss warten, bis ein Platz im Puffer frei wird
			while (s.hasNext()) {
				byte[] sendData = new byte[UDP_PACKET_SIZE];

				// Bytes aus der Datei auslesen, bis Maxanzahl erreicht ist
				for (int i = 0; i < UDP_PACKET_SIZE && s.hasNextByte(); i++) {
					sendData[i] = s.nextByte();
				}
				
				System.out.println("PAKET MIT SEQNUM: " + nextSeqNum + " SOLL HINZUGEFÜGT WERDEN");
				
				//Paket zum Puffer hinzufügen
				addPacket(new FCpacket(nextSeqNum, sendData, sendData.length));
				
				System.out.println("PAKET HINZUGEFÜGT UND VERSCHICKT\n");
			}
		} catch (SocketException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Implementation specific task performed at timeout
	 */
	public  void timeoutTask(long seqNum) {
		// ToDo: RN Folie 3 Seite 55 - Round Trip Time und Timeout
		
		System.out.println("PACKET MIT SEQNUM: " + seqNum + " TIMED OUT");
		
	}

	/**
	 * 
	 * Computes the current timeout value (in nanoseconds)
	 */
	public void computeTimeoutValue(long sampleRTT) {
		// ToDo
	}	
	
	/**
	 * Diese Methode fügt dem Sendepuffer ein Paket hinzu
	 * @param FCpacket packet - erwartet ein Paket, welches verschickt werden soll
	 */
	public void addPacket(FCpacket packet) {
		System.out.println("PACKET MIT SEQNUM: " + packet.getSeqNum() 
				+ " VERSUCHT IN SEMAPHOR EINZUTRETEN THREADNAME: " + Thread.currentThread().getName());
		// Erkaubnis erhalten etwas in den Puffer zu legen --> Puffer noch freie Plätze?
		try {
			freiePlaetze.acquire();
		} catch (InterruptedException e1) {

		}
		
		//Mutex für Pufferzugriff
		try {
			mutex.acquire();
		} catch(InterruptedException e) {

		}
		
		//Nur Thread zurzeit darf auf den sendePuffer zugreifen
		System.out.println("ADD PACKET FÜR SEQNUM: " + packet.getSeqNum() 
				+ " SEMAPHOR BETRETEN DURCH " + Thread.currentThread().getName());
				
		//Paket dem Sendepuffer hinzufügen
		sendBuffer.add(packet);
		
		mutex.release();

		//Paket losschicken
		System.out.println("THREAD ZUM PAKET VERSCHICKEN GESTARTET");
		new SendPacket(clientSocket, this, servername, SERVER_PORT, packet).start();
		
		//Auf Antwort ACK warten
		new ReceiveAcknowledgement(clientSocket, this).start();
		
		//Timer für das Paket starten
		FC_Timer timer = new FC_Timer(timeoutValue, this, nextSeqNum);
		packet.setTimer(timer);
		System.out.println("TIMER FÜR PAKET MIT SEQNUM: " + packet.getSeqNum() + " GESTARTET");
		timer.start();

		//nextSeqNum erhöhen
		nextSeqNum++;
	}
	
	/**
	 * Holt die Acked packete aus dem Sendepuffer
	 * @param long seqNum - erwartet die seqNum des raus zu holenden paketes
	 * synchronized, da nur 1 Thread zurzeit Zugriff auf den Sendepuffer haben soll
	 */
	public void acknowledgedPacket(long seqNum) {
		System.out.println("ACKNOWLEDGE PACKET BETRETEN VON: " + Thread.currentThread().getName());
		FCpacket deletePacket = null;
		
		//puffer Zugriff synchronisieren
		try{
			mutex.acquire();
		} catch(InterruptedException e) {
			
		}
		
		for(FCpacket packet : sendBuffer) {
			//Paket mit übergebener seqNum lokalisieren
			if(packet.getSeqNum() == seqNum) {
				//Paket auf Acknowledged setzten
				packet.setValidACK(true);
				packet.getTimer().interrupt();
				deletePacket = packet;
				
				System.out.println("PACKET MIT SEQNUM: " + seqNum + " AUF ACKNOWLEDGED GESETZT");
			}
		}
		
		sendBuffer.remove(deletePacket);
		
		mutex.release();
		
		//Window um einen Platz verschieben --> Platz im Puffer freigen
		freiePlaetze.release();

		System.out.println("ACKNOWLEDG PACKET WIRD VERLASSEN");
	}
	
	/**
	 * Versendet das erste "spezielle" Pakete
	 */
	private void sendFirstPacket() {	
		//RN Folie 3 Seite 40 - Selective Repeat
		//Erstes Paket verschicken --> Sonderfall
		FCpacket firstPacket = makeControlPacket();
		firstPacket.setTimestamp(System.nanoTime());
		
		addPacket(firstPacket);
	}
	//*********************************************************************************************************

	/**
	 * 
	 * Timer Operations
	 */
	public void startTimer(FCpacket packet) {
		/* Create, save and start timer for the given FCpacket */
		FC_Timer timer = new FC_Timer(timeoutValue, this, packet.getSeqNum());
		packet.setTimer(timer);
		timer.start();
	}

	public void cancelTimer(FCpacket packet) {
		/* Cancel timer for the given FCpacket */
		testOut("Cancel Timer for packet" + packet.getSeqNum());

		if (packet.getTimer() != null) {
			packet.getTimer().interrupt();
		}
	}

	/**
	 * 
	 * Return value: FCPacket with (0 destPath;windowSize;errorRate)
	 */
	public FCpacket makeControlPacket() {
		/*
		 * Create first packet with seq num 0. Return value: FCPacket with (0
		 * destPath ; windowSize ; errorRate)
		 */
		String sendString = destPath + ";" + windowSize + ";" + serverErrorRate;
		byte[] sendData = null;
		try {
			sendData = sendString.getBytes("UTF-8");
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		return new FCpacket(0, sendData, sendData.length);
	}

	public void testOut(String out) {
		if (TEST_OUTPUT_MODE) {
			System.err.printf("%,d %s: %s\n", System.nanoTime(), Thread
					.currentThread().getName(), out);
		}
	}

	public static void main(String[] argv) throws Exception {
		/**
		 * argv[0]: Hostname (String)
		 * argv[1]: Quellpfad (inkl. Dateiname) der zu sendenden lokalen Datei (String)
		 * argv[2]: Zielpfad (inkl. Dateiname) der zu empfangenden Datei (falls bereits vorhanden,
		 * 				wird die Datei überschrieben) (String)
		 * argv[3]: Window-Größe N (int)
		 * argv[4]: Fehlerrate (Error-Rate) zur Auswertung für den Server (long)
		 * 
		 * Einstellen der Parameter: Projekt --> Rechtsklick --> Run As --> Run Configurations --> (Tab) Arguments
		 */
		FileCopyClient myClient = new FileCopyClient(argv[0], argv[1], argv[2],
				argv[3], argv[4]);
		myClient.runFileCopyClient();
	}

}
