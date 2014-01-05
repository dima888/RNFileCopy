package filecopy;

/* FileCopyClient.java
 Version 0.1 - Muss erg�nzt werden!!
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

	//********************* Konstanten *****************************
	public final static boolean TEST_OUTPUT_MODE = false;

	public final int SERVER_PORT = 23_000;

	public final int UDP_PACKET_SIZE = 1008;
	
	public final int DATA_SIZE = UDP_PACKET_SIZE - 8;

	//********************* Public Attribute *****************************
	public String servername;

	public String sourcePath;

	public String destPath;

	public int windowSize;

	public long serverErrorRate;

	//********************* Private Attribute *****************************
	//Sequenznummmer des zu letzt verschickten Paketes
	private long nextSeqNum = 0;
	
	//Sequenznummer des �ltesten Paketes, f�r welches noch kein ACK vorliegt
	private long sendBase = 0;
	
	//Standart UDP-Socket Klasse
	private DatagramSocket clientSocket;
	
	//Sende Puffer
	private List<FCpacket> sendBuffer = new ArrayList<>();
	
	//Anzahl freier pufferpl�tze
	private Semaphore freiePlaetze; 
	
	//Nur ein Thread zurzeit darf auf den sendepuffer zugreifen
	private Semaphore mutex = new Semaphore(1);
	
	//Path Objekt zur Datei
	private Path path;
	
	//Zum byteweise auslesen einer Datei
	private FileInputStream fileInputStream;
	
	//Misst die Anzahl der Timeouts f�r Pakete
	private int timeOutCount = 0;
	
	//Enth�lt die Dateigr��e, welche kopiert werden soll
	private final int FILE_SIZE;
	
	//Soll die Gesamt-�bertragungszeit speichern
	private long transferTime;
    
    //********************* RTT BESTIMMUNG ****************************
	// current default timeout in nanoseconds
	private long timeoutValue = 1_000_000_000l;
	private long estimatedRTT = timeoutValue;
	private long deviation = timeoutValue;
	private long sampleRTTALL;
	private int countRTT;
	
	//********************* TEST AUSGABEN *****************************
	//F�r Ack Ausgaben
	private boolean acknowledgePacketTestOutputMode = false;
	
	//F�r Send Ausgaben
	private boolean sendPacketTestOutputMode = false;
	
	//F�r Pakete hinzuf�gen
	private boolean addPacketTestOutputMode = false;

	// Constructor
	public FileCopyClient(String serverArg, String sourcePathArg,
			String destPathArg, String windowSizeArg, String errorRateArg) {
		servername = serverArg;
		sourcePath = sourcePathArg;
		destPath = destPathArg;
		windowSize = Integer.parseInt(windowSizeArg);
		serverErrorRate = Long.parseLong(errorRateArg);
		
		path = Paths.get(sourcePath);
		
		FILE_SIZE = (int) path.toFile().length();
		
		try {
			fileInputStream = new FileInputStream(path.toFile());
		} catch (FileNotFoundException e) {
			System.err.println("Datei: " + path.getFileName() + " unter dem Pfad: " + path + " nicht gefunden!");
		}
		freiePlaetze = new Semaphore(windowSize);
	}

	public void runFileCopyClient() {
		try {
			System.out.println("Transfer gestartet, bitte einen Augenblick Geduld");
			
			//Startzeitpunkt des Transfers speichern
			transferTime = System.currentTimeMillis();
			
			//Socketverbindung initialisieren
			clientSocket = new DatagramSocket();
			
			//Thread zum lauschen auf Server antworten (Acks) starten
			new ReceiveAcknowledgement(clientSocket, this, path.toFile().length()).start();
			
			//Flag f�r das erste Paket
			boolean firstPacketSend = true;
			
			//L�uft solange, bis mehr Bytes zur Verf�gung stehen, als DATA_SIZE gro� ist
			while(fileInputStream.available() > DATA_SIZE) {
				
				//Erstes Paket besonders behandeln
				if(firstPacketSend) {
					firstPacketSend = false;
					
					//Erstes Paket verschicken --> Sonderfall
					FCpacket firstPacket = makeControlPacket();
					
					//Erstes Paket dem Sendepuffer hinzuf�gen
					addPacket(firstPacket);
				} else {
					//Pufferspeicher f�r Daten als Bytes
					byte[] sendData = new byte[DATA_SIZE];
					
					try {
						//Lie�t solange Bytes aus der Datei ein, bis das Byte-Array voll ist
						fileInputStream.read(sendData);
					
						//Paket zum Puffer hinzuf�gen
						addPacket(new FCpacket(nextSeqNum, sendData, sendData.length));
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
			
			//Berechnung der �brig gebliebenen Bytes der Datei
			int rest = (FILE_SIZE % DATA_SIZE);
			
			//Byte-Array mit gr��e der restlichen Bytes initialisieren
			byte[] sendData = new byte[rest];
			
			//restlichen Bytes einlesen
			fileInputStream.read(sendData);
			
			//Paket zum Puffer hinzuf�gen
			addPacket(new FCpacket(nextSeqNum, sendData, sendData.length));
		} catch (SocketException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

		//Geforderte Ergebnisausgaben
		System.out.println("Gesamt-�bertragungszeit in Millisekunden: " + ((transferTime - System.currentTimeMillis()) * -1) );
		System.out.println("Anzahl an Timerabl�ufen: " + timeOutCount);
		System.out.println("Gemessener Mittelwert f�r die RTT: " + timeoutValue);
	}
	
	/**
	 * Implementation specific task performed at timeout
	 * Synchronized, da nur ein Thread zurzeit zugriff auf sendepuffer haben soll
	 */
	public void timeoutTask(long seqNum) {
		//Mutex f�r Pufferzugriff
		try {
			mutex.acquire();
		} catch(InterruptedException e) {

		}
		
		//Counter f�r Timeouts inkrementieren
		timeOutCount++;
		
		for(FCpacket packet : sendBuffer) {
			//Paket mit �bergebener seqNum lokalisieren
			if(packet.getSeqNum() == seqNum) {
				//Paket erneut losschicken
				new SendPacket(clientSocket, this, servername, SERVER_PORT, packet).start();
				
				//Timer f�r das Paket erneut starten
				FC_Timer timer = new FC_Timer(timeoutValue, this, packet.getSeqNum());
				packet.setTimer(timer);
				timer.start();
			}
		}
		mutex.release();
	}

	/**
	 * Computes the current timeout value (in nanoseconds)
	 * Kapitel 3 Folie 55 RN 2013
	 */
	public void computeTimeoutValue(long sampleRTT) {
		sampleRTTALL += sampleRTT;
		countRTT++;

		estimatedRTT = Double.valueOf(
				(1 - 0.1) * estimatedRTT + 0.1 * sampleRTT).longValue();

		deviation = Double.valueOf(
				(1 - 0.1) * deviation + 0.1
						* Math.abs(sampleRTT - estimatedRTT)).longValue();

		timeoutValue = estimatedRTT + 4 * deviation;
	}	
	
	/**
	 * Diese Methode f�gt dem Sendepuffer ein Paket hinzu
	 * @param FCpacket packet - erwartet ein Paket, welches verschickt werden soll
	 */
	public void addPacket(FCpacket packet) {		
		//Erkaubnis erhalten etwas in den Puffer zu legen --> Puffer noch freie Pl�tze?
		try {
			freiePlaetze.acquire();
		} catch (InterruptedException e1) {

		}
		
		//Mutex f�r Pufferzugriff
		try {
			mutex.acquire();
		} catch(InterruptedException e) {

		}
				
		//Paket dem Sendepuffer hinzuf�gen
		sendBuffer.add(packet);
		
		if(addPacketTestOutputMode) {
			System.out.println("Packet mit SeqNum: " 
					+ packet.getSeqNum() 
					+ " wurde dem sendePuffer hinzugef�gt\n"
					+ "sendePuffer Inhalt(addPacket): "
					+ sendBuffer);
		}
		
		mutex.release();

		//Paket losschicken
		new SendPacket(clientSocket, this, servername, SERVER_PORT, packet).start();
		
		if(sendPacketTestOutputMode) {
			System.out.println("Paket mit SeqNum:"
					+ packet.getSeqNum()
					+ " wurde verschickt");
		}
		
		//Zeit festlegen --> Zeitstempel
		packet.setTimestamp(System.nanoTime());
		
		//Timer f�r das Paket starten
		startTimer(packet);

		//nextSeqNum erh�hen
		nextSeqNum++;
	}
	
	/**
	 * Holt die Acked packete aus dem Sendepuffer
	 * @param long seqNum - erwartet die seqNum des raus zu holenden paketes
	 */
	public void acknowledgedPacket(long seqNum) {
		if(acknowledgePacketTestOutputMode) {
			System.out.println("Ack f�r Paket mit SeqNum:"
					+ seqNum
					+ " eingetroffen");
		}
		
		//puffer Zugriff synchronisieren
		try{
			mutex.acquire();
		} catch(InterruptedException e) {
			
		}
		
		//Flag f�r sendbase
		boolean isSendBase = false;
		
		//Paket suchen und auf acked setzen
		for(FCpacket packet : sendBuffer) {
			//Paket mit �bergebener seqNum lokalisieren
			if(packet.getSeqNum() == seqNum) {
				//Markiere Paket als quittiert
				packet.setValidACK(true);
				
				//Timer f�r Paket stoppen
				cancelTimer(packet);	
				
				//Timeoutwert mit gemessener RTT f�r Paket neu berechnen
				//TODO: WAS GENAU MUSS HIER AUFGERUFEN WERDEN ???
				//timeoutTask(timeoutValue);
				//timeoutTask(packet.getTimestamp());
				
				//Pr�fen ob das Ack f�r die sendBase eingetroffen ist
				if(packet.getSeqNum() == sendBase) {
					isSendBase = true;
				}
			}
		}
		
		//Falls die Seqnum eines erhaltenen AckPackets �bereinstimmt
		if(isSendBase) {
			if(acknowledgePacketTestOutputMode) {
				System.out.println("Sendbase Ack eingetroffen Paket SeqNum:"
						+ sendBase);
			}
			
			//Pufferspeicher f�r das entfernen der Packete aus sendBuffer
			List<FCpacket> removePackets = new ArrayList<>();
			
			for(FCpacket packet : sendBuffer) {
				if(packet.isValidACK()) {
					//Alles ab sendBase was Acked ist, in den puffer ablegen
					removePackets.add(packet);
					
					//Sendbase auf Paket SeqNum n + 1 setzten
					sendBase = packet.getSeqNum() + 1;
				} else {					
					//Durchlauf abbrechen, sobald ein Packet kommt, dass nicht Acked ist
					break;
				}
			}
			
			if(acknowledgePacketTestOutputMode) {
				System.out.println("Inhalt des sendePuffers(acknowledgePacket): "
						+ sendBuffer
						+ "\nAcknowledged sind davon:"
						+ removePackets
						+ "\nSendbase ist:"
						+ sendBase);
			}
			
			//Acked pakete bis ein nicht acked Paket l�schen und dies auf Sendbase setzten
			sendBuffer.removeAll(removePackets);
			
			if(acknowledgePacketTestOutputMode) {
				System.out.println("Acked Pakete entfernt, sendePuffer Inhalt(acknowledgePacket):"
						+ sendBuffer);
			}
			
			//Pl�tze im Puffer frei geben
			for(int i = 0; i < removePackets.size(); i++) {
				//Window um einen Platz verschieben --> Platz im Puffer freigeben
				//Soviele Pl�tze freigeben, wie Packete gel�scht wurden
				freiePlaetze.release();
			}
		}
		
		//Platz freigeben
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
		 * 				wird die Datei �berschrieben) (String)
		 * argv[3]: Window-Gr��e N (int)
		 * argv[4]: Fehlerrate (Error-Rate) zur Auswertung f�r den Server (long)
		 * 
		 * Einstellen der Parameter: Projekt --> Rechtsklick --> Run As --> Run Configurations --> (Tab) Arguments
		 */
//		FileCopyClient myClient = new FileCopyClient(argv[0], argv[1], argv[2],
//				argv[3], argv[4]);
		FileCopyClient myClient = new FileCopyClient("localhost", "FCData.pdf", "FCData_�bertragen.pdf", "5", "10");
//		FileCopyClient myClient = new FileCopyClient("localhost", "Sem_BAI4.pdf", "Sem_BAI4_�bertragen.pdf", "1", "1000");
//		FileCopyClient myClient = new FileCopyClient("localhost", "TestFile.txt", "TestFile_�bertragen.txt", "1", "1000");
		myClient.runFileCopyClient();
	}

}