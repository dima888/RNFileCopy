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
	private long sendBase = 0;
	
	private DatagramSocket clientSocket;
	
	//Sende Puffer
	private List<FCpacket> sendBuffer = new ArrayList<>();
	
	//Anzahl freier pufferplätze
	private Semaphore freiePlaetze; 
	
	//Nur ein Thread zurzeit darf auf den sendepuffer zugreifen
	private Semaphore mutex = new Semaphore(1);
	
	//Path Objekt zur Datei
	private Path p;
	
	//Zum byteweise auslesen einer Datei
	private FileInputStream fileInputStream;
	
	//Misst die Anzahl der Timeouts für Pakete
	private int timeOutCount = 0;

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
			fileInputStream = new FileInputStream(p.toFile());
		} catch (FileNotFoundException e) {
			System.err.println("Datei: " + p.getFileName() + " unter dem Pfad: " + p + " nicht gefunden!");
		}
		freiePlaetze = new Semaphore(windowSize);
	}
	
	//*************************************SELBST IMPLEMENTIERT*********************************************
	public void runFileCopyClient() {
		try {
			//socket Verbindung initialisieren
			clientSocket = new DatagramSocket();
			
			//Thread zum lauschen auf Server antworten (Acks) starten
			new ReceiveAcknowledgement(clientSocket, this).start();
			
			//Erstes spezial Paket verschicken
			sendFirstPacket();
			
			//TODO: RESTLICHE BYTES AUSLESEN
			while(fileInputStream.available() > UDP_PACKET_SIZE) {
				byte[] sendData = new byte[UDP_PACKET_SIZE];
				
				try {
					fileInputStream.read(sendData);
					
					//Paket zum Puffer hinzufügen
					addPacket(new FCpacket(nextSeqNum, sendData, sendData.length));
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		} catch (SocketException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		//Geforderte Ergebnisausgaben
		//1. Gesamt-Übertragungszeit für eine Datei
		//2. Anzahl an Timerabläufen
		//3. der gemessene Mittelwert für die RTT
		System.out.println("Anzahl an Timerabläufen: " + timeOutCount);
	}
	
	/**
	 * Implementation specific task performed at timeout
	 * Synchronized, da nur ein Thread zurzeit zugriff auf sendepuffer haben soll
	 */
	public synchronized void timeoutTask(long seqNum) {
		//Counter für Timeouts inkrementieren
		timeOutCount++;
		
		for(FCpacket packet : sendBuffer) {
			//Paket mit übergebener seqNum lokalisieren
			if(packet.getSeqNum() == seqNum) {
				//Paket erneut losschicken
				new SendPacket(clientSocket, this, servername, SERVER_PORT, packet).start();
				
				//Timer für das Paket erneut starten
				FC_Timer timer = new FC_Timer(timeoutValue, this, nextSeqNum);
				packet.setTimer(timer);
				timer.start();
			}
		}
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
		//Erkaubnis erhalten etwas in den Puffer zu legen --> Puffer noch freie Plätze?
		try {
			freiePlaetze.acquire();
		} catch (InterruptedException e1) {

		}
		
		//Mutex für Pufferzugriff
		try {
			mutex.acquire();
		} catch(InterruptedException e) {

		}
				
		//Paket dem Sendepuffer hinzufügen
		sendBuffer.add(packet);
		
		mutex.release();

		//Paket losschicken
		new SendPacket(clientSocket, this, servername, SERVER_PORT, packet).start();
		
		//Timer für das Paket starten
		FC_Timer timer = new FC_Timer(timeoutValue, this, nextSeqNum);
		packet.setTimer(timer);
		timer.start();

		//nextSeqNum erhöhen
		nextSeqNum++;
	}
	
	/**
	 * Holt die Acked packete aus dem Sendepuffer
	 * @param long seqNum - erwartet die seqNum des raus zu holenden paketes
	 */
	public void acknowledgedPacket(long seqNum) {
		//TODO: TIMEOUTWERT mit gemessener RTT für PAKET n neu berechnen
		
		//puffer Zugriff synchronisieren
		try{
			mutex.acquire();
		} catch(InterruptedException e) {
			
		}
		
		List<FCpacket> ackedPackets = new ArrayList<>();
		
		for(FCpacket packet : sendBuffer) {
			//Flag zum überprüfen, ob das acked paket die sendbase ist
			boolean isSendBase = false;
			
			//Paket mit übergebener seqNum lokalisieren
			if(packet.getSeqNum() == seqNum) {
				//Markiere Paket als quittiert
				packet.setValidACK(true);
				
				//Timer für Paket stoppen
				packet.getTimer().interrupt();
				
				//wenn n = sendbase, dann lösche ab n alle Pakete, bis ein noch nicht
				//quittiertes Paket im sendepuffer erreicht ist und setzte sendbase
				//auf dessen sequenznummer
				if(seqNum == sendBase) {
					isSendBase = true;
				}				
			}
			
			//Alle Pakete löschen bis ein noch nicht quittiertes kommt und dieses
			//auf sendbase setzten
			if(isSendBase) {
				if(packet.isValidACK()) {
					ackedPackets.add(packet);
				} else {
					//Falls ein packet gefunden wird, welches nicht quittiert ist
					isSendBase = false;
					//setzen auf sendbase
					sendBase = packet.getSeqNum();
				}
			}
		}
		
		//Alle quittierten Packete aus dem sendepuffer löschen
		sendBuffer.removeAll(ackedPackets);
		
		mutex.release();
		
		//Window um einen Platz verschieben --> Platz im Puffer freigen
		freiePlaetze.release();
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
