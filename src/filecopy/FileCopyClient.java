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

/*
 * TODO Fehler quelle liegt irgendwo beim versenden
 * Der Client schmiert jedes mal ab, wenn Paket mit SeqNum 129 verschickt wird...beim
 * Server kommt aus irgendwelchen GRÜNDEN die SeqNum 63...Bei einer Paketanzahl kleiner
 * 129 kann man Problemlos eine TXT Datei übertragen
 */

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
	
	private final int FILE_SIZE;
	
	private ReceiveAcknowledgement receiver;

	// Constructor
	public FileCopyClient(String serverArg, String sourcePathArg,
			String destPathArg, String windowSizeArg, String errorRateArg) {
		servername = serverArg;
		sourcePath = sourcePathArg;
		destPath = destPathArg;
		windowSize = Integer.parseInt(windowSizeArg);
		serverErrorRate = Long.parseLong(errorRateArg);
		
		p = Paths.get(sourcePath);
		
		FILE_SIZE = (int) p.toFile().length();
		
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
			receiver = new ReceiveAcknowledgement(clientSocket, this, FILE_SIZE);
			receiver.start();
			
			
			//Flag für das erste Paket
			boolean firstPacketSend = true;
			
			while(fileInputStream.available() > DATA_SIZE) {
				
				//Erstes Paket besonders behandeln
				if(firstPacketSend) {
					firstPacketSend = false;
					
					//Erstes Paket verschicken --> Sonderfall
					FCpacket firstPacket = makeControlPacket();
					
					addPacket(firstPacket);
				} else {
					byte[] sendData = new byte[DATA_SIZE];
					
					try {
						fileInputStream.read(sendData);
					
						//Paket zum Puffer hinzufügen
						addPacket(new FCpacket(nextSeqNum, sendData, sendData.length));
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
			
			//Berechnung der übrig gebliebenen Bytes der Datei
			int rest = (FILE_SIZE % DATA_SIZE);
			
			//int rest = 337;
			byte[] sendData = new byte[rest];
			
			fileInputStream.read(sendData);
			
			//Paket zum Puffer hinzufügen
			addPacket(new FCpacket(nextSeqNum, sendData, sendData.length));
		} catch (SocketException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		//TODO HIER IST ES FALSCH PLAZIERT, DA SO NICHT ZWANGSLÄUFIG AUF DAS LETZTE ACK GEWARTET WIRD
		//receiver.setServiceRequestedFalse();
		
		while(true) {
			try {
				Thread.currentThread().sleep(1000);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			System.out.println("*****************Anzahl an Timerabläufen: " + timeOutCount + "*************************");
		}

		//Geforderte Ergebnisausgaben
		//1. Gesamt-Übertragungszeit für eine Datei
		//2. Anzahl an Timerabläufen
		//3. der gemessene Mittelwert für die RTT
		//System.out.println("*****************Anzahl an Timerabläufen: " + timeOutCount + "*************************");
	}
	
	/**
	 * Implementation specific task performed at timeout
	 * Synchronized, da nur ein Thread zurzeit zugriff auf sendepuffer haben soll
	 */
	public void timeoutTask(long seqNum) {
		//Mutex für Pufferzugriff
		try {
			mutex.acquire();
		} catch(InterruptedException e) {

		}
		
		//Counter für Timeouts inkrementieren
		timeOutCount++;
		
		System.out.println("TTTTIIIIIIMMMMMMMMMEEEEEEEEEDDDDDDDDDD OOOOOOOOOUUUUUUUUUTTTTTTTTT SEQNUM: " + seqNum);
		
		for(FCpacket packet : sendBuffer) {
			//Paket mit übergebener seqNum lokalisieren
			if(packet.getSeqNum() == seqNum) {
				//Paket erneut losschicken
				new SendPacket(clientSocket, this, servername, SERVER_PORT, packet).start();
				
				//Timer für das Paket erneut starten
				FC_Timer timer = new FC_Timer(timeoutValue, this, packet.getSeqNum());
				packet.setTimer(timer);
				timer.start();
			}
		}
		mutex.release();
	}

	/**
	 * 
	 * Computes the current timeout value (in nanoseconds)
	 */
	public void computeTimeoutValue(long sampleRTT) {
//		//TODO
//		long timeoutValue = 100000000L;
//		long estimatedRTT = timeoutValue;
//		long deviation = timeoutValue;
//		long sampleRTTALL;
//		int countRTT;
//		
////		sampleRTTALL += sampleRTT;
////		countRTT++;
//
//		estimatedRTT = Double.valueOf(
//				(1 - 0.1) * estimatedRTT + 0.1 * sampleRTT).longValue();
//
//		deviation = Double.valueOf(
//				(1 - 0.1) * deviation + 0.1
//						* Math.abs(sampleRTT - estimatedRTT)).longValue();
//
//		timeoutValue = estimatedRTT + 4 * deviation;
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
		
		//Zeit festlegen --> Zeitstempel
		//packet.setTimestamp(System.nanoTime());
		
		//Timer für das Paket starten
		FC_Timer timer = new FC_Timer(timeoutValue, this, packet.getSeqNum());
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
		
		//Pufferspeicher für alle Acked Packets
		List<FCpacket> ackedPackets = new ArrayList<>();
		boolean isSendBase = false;
		
		for(FCpacket packet : sendBuffer) {
			//Paket mit übergebener seqNum lokalisieren
			if(packet.getSeqNum() == seqNum) {
				//Markiere Paket als quittiert
				packet.setValidACK(true);
				
				//Timer für Paket stoppen
				packet.getTimer().interrupt();		
				
				//Zu liste mit ack packets hinzufügen
				ackedPackets.add(packet);
			}
		}
		
		//Heraus finden ob wir schon ein Ack für die sendBase erhalten haben
		for(FCpacket packet : ackedPackets) {
			//Nach sendBase suchen
			if(packet.getSeqNum() == sendBase) {
				//Falls Seqnum mit sendBase übereinstimmt, flag auf true setzten
				isSendBase = true;
			}
		}
		
		//Falls die Seqnum eines erhaltenen AckPackets übereinstimmt
		if(isSendBase) {
			//Pufferspeicher für das entfernen der Packete aus sendBuffer
			List<FCpacket> removePackets = new ArrayList<>();
			
			for(FCpacket packet : sendBuffer) {
				if(packet.isValidACK()) {
					//Alles ab sendBase was Acked ist, in den puffer ablegen
					removePackets.add(packet);
				} else {
					//TODO	WAS PASSIERT, WENN WINDOW SIZE = 1 IST	TODO
					//TODO	AUF WAS WIRD SENDBASE DANN GESETZT ???	TODO
					System.out.println("***************ALTE SENDBASE: " + sendBase + "**********************");
					//Packetseqnum auf sendbase setzten
					sendBase = packet.getSeqNum();
					System.out.println("***************NEUE SENDBASE: " + sendBase + "**********************");
					
					//Durchlauf abbrechen, sobald ein Packet kommt, dass nicht Acked ist
					break;
				}
			}
			
			//EIGENE LÖSUNG ABER IST DAS RICHTIG ???
			if(windowSize == 1) {
				sendBase++;
			}
			
			//Acked pakete bis ein nicht acked Paket löschen und dies auf Sendbase setzten
			sendBuffer.removeAll(removePackets);
			
			for(int i = 0; i < removePackets.size(); i++) {
				//Window um einen Platz verschieben --> Platz im Puffer freigeben
				//Soviele Plätze freigeben, wie Packete gelöscht wurden
				freiePlaetze.release();
			}
		}
		
		mutex.release();
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
//		FileCopyClient myClient = new FileCopyClient(argv[0], argv[1], argv[2],
//				argv[3], argv[4]);
		//FileCopyClient myClient = new FileCopyClient("localhost", "FCData.pdf", "FCData1.pdf", "1", "1000");
		FileCopyClient myClient = new FileCopyClient("localhost", "Sem_BAI4.pdf", "Sem_BAI4AFJHOA.pdf", "10", "1000");
		//FileCopyClient myClient = new FileCopyClient("localhost", "TestFile.txt", "TestFile1.txt", "1", "1000");
		myClient.runFileCopyClient();
	}

}
