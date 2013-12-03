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

public class FileCopyClient extends Thread {

	// -------- Constants
	public final static boolean TEST_OUTPUT_MODE = false;

	public final int SERVER_PORT = 23000;

	public final int UDP_PACKET_SIZE = 1008;

	// -------- Public parms
	public String servername;

	public String sourcePath;

	public String destPath;

	public int windowSize;

	public long serverErrorRate;

	// -------- Variables
	// current default timeout in nanoseconds
	private long timeoutValue = 100000000L;

	//Sequenznummmer des zu letzt verschickten Paketes --> 1 da 0 für die initialisierung festgelegt ist
	private long nextSeqNum = 1;
	
	//Sequenznummer des ältesten Paketes, für welches noch kein ACK vorliegt --> 1 da 0 für die initialisierung festgelegt ist
	private long sendBase = 1;
	
	//Sende Puffer
	private List<FCpacket> sendePuffer = new ArrayList<>();
	
	//Path Objekt zur Datei
	private Path p;
	
	//Datei speichern, die unter dem Pfad liegt
	private File f;
	
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

	}
	
	//*************************************SELBST IMPLEMENTIERT*********************************************
	public void runFileCopyClient() {
		//RN Folie 3 Seite 40 - Selective Repeat
		//Erstes Paket verschicken --> Sonderfall
		FCpacket firstPacket = makeControlPacket();
		new SendPacket(firstPacket, SERVER_PORT);
		
		try {
			//Path Objekt erzeugen zum String Path
			p = Paths.get(sourcePath);
			
			//Datei Obejkt erzeugen zum Path
			f = p.toFile();
			
			//Scanner initialisieren
			s = new Scanner(f);
		} catch (FileNotFoundException e) {
			System.err.println("Datei: " + f.toString() + " unter dem Pfad: " + p + " nicht gefunden!");
		}
		
		//Konsolenausgaben zur prüfung
		System.out.println("Größe der Datei: " + f.length() + " UDP_PACKET_SIZE: " + UDP_PACKET_SIZE);
		
		while (s.hasNextByte()) {
			// maximale größe eines Packets
			byte[] sendData = new byte[UDP_PACKET_SIZE];

			// Bytes aus der Datei auslesen, bis Maxanzahl erreicht ist
			for (int i = 0; i < UDP_PACKET_SIZE && s.hasNextByte(); i++) {
				sendData[i] = s.nextByte();
			}
			
			

			//Neues SendPacket Objekt erstellen und ein FCpacket Objekt übergeben, sowie SERVER_PORT
			new SendPacket(new FCpacket(nextSeqNum, sendData, sendData.length), SERVER_PORT);
		}
		
	}
	
	/**
	 * Implementation specific task performed at timeout
	 */
	public void timeoutTask(long seqNum) {
		// ToDo: RN Folie 3 Seite 55 - Round Trip Time und Timeout
	}

	/**
	 * 
	 * Computes the current timeout value (in nanoseconds)
	 */
	public void computeTimeoutValue(long sampleRTT) {

		// ToDo
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
