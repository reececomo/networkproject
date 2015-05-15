import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.*;

import javax.net.ssl.*;

import lib.*;

/**
 * Director Node class
 * 
 * @author Jesse Fletcher, Caleb Fetzer, Reece Notargiacomo, Alex Popoff
 * @version 5.9.15
 *
 */
public class Director extends DemoMode {
	
	private static final int PORT = 9998;
	private SSLServerSocket director;

	private HashMap<String, HashSet<String>> analystPool;	// explained in constuctor
	private HashSet<String> busyAnalyst;			// as above
	
	private boolean socketIsListening = true;
	
	// main
	public static void main(String[] args) {
		Director myDir = new Director( PORT );
	}
	
	//constructor
	public Director ( int portNo ) {
		
		analystPool = new HashMap<String, HashSet<String>>();		// hashmap of data types, storing all [address:socket] of analyst's assoc. with data type
		busyAnalyst = new HashSet<String>();				// busy analysts address pool (all analysts in here are currently busy)


		// SSL Certificate

		SSLHandler.declareClientCert("cits3002_01Keystore","cits3002");
		SSLHandler.declareServerCert("cits3002_01Keystore","cits3002");
		ExecutorService executorService = Executors.newFixedThreadPool(100);
		
		// Start Server and listen
		if( this.startSocket(portNo) ) {
			while(this.socketIsListening){
				try {	
					SSLSocket clientSocket = (SSLSocket) director.accept();
					executorService.execute(new DirectorClient ( clientSocket ));
					System.out.println("New client");
			
				} catch (IOException err) {
				
					System.err.println("Error connecting client " + err);
				}
			}
		}
	}
	
	public boolean startSocket( int portNo ) {
		try {
			SSLServerSocketFactory sf;
			
			sf = (SSLServerSocketFactory) SSLServerSocketFactory.getDefault();
			director = (SSLServerSocket) sf.createServerSocket( portNo );
			
			System.out.println("Director started on " + getIPAddress() + ":" + portNo );
			
			return true;
			
		} catch (Exception error) {
			System.err.println("Director failed to start: " + error );
			System.exit(-1);
			
			return false;
		}
	}

	
	public String getIPAddress() {
		try {
			//Get IP Address
			return InetAddress.getLocalHost().getHostAddress();
		} catch (UnknownHostException e) {
			return "UnknownHost";
		}
	}
	

	public class DirectorClient implements Runnable {

		private String inmessage, outmessage;

		protected SSLSocket sslsocket = null;

		public DirectorClient(SSLSocket socket){
			this.sslsocket = socket;
		}

		public void run() {
			try {
				InputStream inputstream = sslsocket.getInputStream();
				InputStreamReader inputstreamreader = new InputStreamReader(inputstream);

				BufferedReader bufferedreader = new BufferedReader(inputstreamreader);

				OutputStream outputstream = sslsocket.getOutputStream();
            	OutputStreamWriter outputstreamwriter = new OutputStreamWriter(outputstream);
		
				String message = bufferedreader.readLine();
				Message msg = new Message(message);
				
				System.out.println("FLAG = " + msg.getFlag());
				// this may be put in a protocol LATER

				if(msg.getFlag().equals(MessageFlag.C_INIT)){		// Collector init
					System.out.println("Collector Initialized..");

					if(analystPool.containsKey(msg.data)){
						outputstreamwriter.write("TRUE\n");
					}else{
						outputstreamwriter.write("FALSE\n");
					}
					outputstreamwriter.flush();

				}else if(msg.getFlag().equals(MessageFlag.A_INIT)){	// Analysis init
				
					// Analyst INIT message = [ INITFLAG  :  DATA TYPE  ;  ADDRESS  ;  PORT ]   where address/port is what analyst server is listening on
					//					   t		 a	   p

					String temp = msg.data;
					String t = temp.split(";")[0];		// get analyst data type
					String a = temp.split(";")[1];		// get analyst listening address
					String p = temp.split(";")[2];		// get analyst listening port
					
					if( !analystPool.containsKey(t) ){
						HashSet<String> set = new HashSet<String>();
						set.add(a + ":" + p);		// add Host:Port of analyst to hashset
						analystPool.put(t, set);	// add analyst to datatype pool and socket set
					}else{
						analystPool.get(t).add(a + ":" + p);
					}
				
					System.out.println("Analyst Initialized with data type: " + t);

				}else if(msg.getFlag().equals(MessageFlag.EXAM_REQ)){		// Analysis request (examination)

					HashSet<String> disconnectedAnalyst = new HashSet<String>();

					System.out.println("Data Analysis request recieved");

					boolean success = false;

					String temp = msg.data;
					String t = temp.split(";")[0];		// get collector data type
					String d = temp.split(";")[1];		// get collector data
					String c = temp.split(";")[2];		// get collector ecent
					
					HashSet<String> getAnalysts = analystPool.get(t);	// get analyst that match data type

					for(String address : getAnalysts){			// for each analyst in data type
						if(!busyAnalyst.contains(address)){		// if their address isn't currently busy
							
							outmessage = d + ";" + c + "\n";
							if(sendDataToAnalyst(outmessage, address)){
								if(outmessage != null){
									outputstreamwriter.write(outmessage);
									outputstreamwriter.flush();
									System.out.println("Result successfully returned to collector");
									success = true;
									break;
								}else 
									System.out.println("Analyst crashed after recieving ecent.. trying next one");
							}else{
								disconnectedAnalyst.add(address);	// add analyst to DCed set if connection fails
							}
						}
					}

					if(!success)
						System.out.println("Analysis could not be completed...");

					if(!disconnectedAnalyst.isEmpty()){
						for(String s : disconnectedAnalyst){
							analystPool.get(t).remove(s);		// remove DCed analyst from pool
						}
					}

				}

				sslsocket.close();

			} catch (IOException e){
           	 	System.out.println("Error listening on port or listening for a connection");
				System.out.println(e.getMessage());
    		
    		}
		}
	
		private boolean sendDataToAnalyst(String message, String address){
			
			String host = address.split(":")[0];
			int port = Integer.parseInt(address.split(":")[1]);

			try{
				SSLSocketFactory sslsf = (SSLSocketFactory)SSLSocketFactory.getDefault();
				SSLSocket sslsocket = (SSLSocket)sslsf.createSocket(host, port);

				InputStream inputstream = sslsocket.getInputStream();
				InputStreamReader inputstreamreader = new InputStreamReader(inputstream);
				BufferedReader bufferedreader = new BufferedReader(inputstreamreader);

				OutputStream outputstream = sslsocket.getOutputStream();
       		     		OutputStreamWriter outputstreamwriter = new OutputStreamWriter(outputstream); 

				busyAnalyst.add(address);			// connected so add to busy analysts

				System.out.println("Sending Data to Analyst on " + address);

				outputstreamwriter.write(outmessage);		// send message to analyst
				outputstreamwriter.flush();

				outmessage = bufferedreader.readLine(); // get message to forward to collector

				busyAnalyst.remove(address);	
				return true;

			}catch (IOException e)
			{
				System.out.println("Could not connect to Analyst on " + address + ", trying next one..");
			}

			return false;
		
		}
			
	}
	
}
