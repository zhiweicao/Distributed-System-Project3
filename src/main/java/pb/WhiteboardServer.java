package pb;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import pb.managers.endpoint.Endpoint;
import pb.managers.ClientManager;
import pb.managers.IOThread;
import pb.managers.ServerManager;
import pb.utils.Utils;

/**
 * Simple whiteboard server to provide whiteboard peer notifications.
 * @author aaron
 *
 */
public class WhiteboardServer {
	private static Logger log = Logger.getLogger(WhiteboardServer.class.getName());
	
	/**
	 * Emitted by a client to tell the server that a board is being shared. Argument
	 * must have the format "host:port:boardid".
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String shareBoard = "SHARE_BOARD";

	/**
	 * Emitted by a client to tell the server that a board is no longer being
	 * shared. Argument must have the format "host:port:boardid".
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String unshareBoard = "UNSHARE_BOARD";

	/**
	 * The server emits this event:
	 * <ul>
	 * <li>to all connected clients to tell them that a board is being shared</li>
	 * <li>to a newly connected client, it emits this event several times, for all
	 * boards that are currently known to be being shared</li>
	 * </ul>
	 * Argument has format "host:port:boardid"
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String sharingBoard = "SHARING_BOARD";

	/**
	 * The server emits this event:
	 * <ul>
	 * <li>to all connected clients to tell them that a board is no longer
	 * shared</li>
	 * </ul>
	 * Argument has format "host:port:boardid"
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String unsharingBoard = "UNSHARING_BOARD";

	/**
	 * Emitted by the server to a client to let it know that there was an error in a
	 * received argument to any of the events above. Argument is the error message.
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String error = "ERROR";
	
	/**
	 * Default port number.
	 */
	private static int port = Utils.indexServerPort;
	
	/**
	 * Customize Parameters
	 */
	
	/**
	 * The client emit this event to server at the beginning.
	 * Server will add this client into shared client list and send available board to it.
	 */
	public static final String joinNetwork = "JOIN_NETWORK";

	/**
	 * The server emit this event to client as the response.
	 * The response argument will contains all available boards within the network.
	 */
	public static final String availableBoards = "AVAILABLE_BOARDS";

	/**
	 * BoardID map to Board owner 
	 */
	private static final HashMap<String, String> boardMap = new HashMap<>();
	private static final Set<String> clientAddrs = new HashSet<>();


	private static void addNewBoard(String boardID) {
		String[] parts = boardID.split(":",3);
		String hostWithIP = parts[0] + ":" + parts[1];
		log.info("New board has been added into broad list. Board ID: " + boardID);
		boardMap.put(boardID, hostWithIP);
		clientAddrs.add(hostWithIP);
	}

	private static void removeBoard(String boardID) {
		if (boardMap.containsKey(boardID)) {
			boardMap.remove(boardID);
			log.info("The board: " + boardID + " has been removed.");
		} else {
			log.warning("The board: " + boardID + " does not exist.");
		}
	}


	private static void broadCastBoardEventToAll(String event, String boardID) {
		String[] parts = boardID.split(":");
		String boardOwnerAddr = parts[0] + ":" + parts[1];
		for (String addr: clientAddrs) {
			if (addr.equals(boardOwnerAddr)) continue;

			String[] receiverParts = addr.split(":");
			String receiverHost = receiverParts[0];
			int receverPort = Integer.parseInt(receiverParts[1]);

			sendEventToClient(event, boardID, receiverHost, receverPort);
		}
	}
	
	private static void sendEventToClient(String event, String Args, String receiverHost, int receverPort) {
		try {
			ClientManager clientManager = new ClientManager(receiverHost, receverPort);
			clientManager.on(ClientManager.sessionStarted, (args) -> {
				Endpoint endpoint = (Endpoint)args[0];
				log.info("Connected to whiteboard client: "+endpoint.getOtherEndpointId() + ". Attempt to send event: " + event);
				endpoint.emit(event, Args);
				clientManager.shutdown();
			}).on(ClientManager.sessionStopped, (args) -> {
				Endpoint endpoint = (Endpoint)args[0];
				log.info("Disconnected from peer: "+endpoint.getOtherEndpointId());
				clientManager.shutdown();
			}).on(ClientManager.sessionError, (args) -> {
				Endpoint endpoint = (Endpoint)args[0];
				log.info("There was error while communication with peer: "
						+endpoint.getOtherEndpointId());
				clientManager.shutdown();
			});
			clientManager.start();
		} catch (UnknownHostException e) {
			log.info("Unable " + event + " to client. The whiteboard client host could not be found: "+ receiverHost);
		} catch (InterruptedException e) {
			log.info("Unable " + event + " to client. Interrupted while trying to send updates to the client whiteboard.");
		}
	}
	
	public static ArrayList<String> unpackBoardPacket(String packet) {
		return new ArrayList<>(Arrays.asList(packet.split(",")));
	}

	public static String packBoardPacket(ArrayList<String> boards) {
		return String.join(",", boards);
	}

	private static void help(Options options){
		String header = "PB Whiteboard Server for Unimelb COMP90015\n\n";
		String footer = "\ncontact aharwood@unimelb.edu.au for issues.";
		HelpFormatter formatter = new HelpFormatter();
		formatter.printHelp("pb.IndexServer", header, options, footer, true);
		System.exit(-1);
	}
	
	public static void main( String[] args ) throws IOException, InterruptedException
    {
    	// set a nice log format
		System.setProperty("java.util.logging.SimpleFormatter.format",
                "[%1$tl:%1$tM:%1$tS:%1$tL] [%4$s] %2$s: %5$s%n");
        
    	// parse command line options
        Options options = new Options();
        options.addOption("port",true,"server port, an integer");
        options.addOption("password",true,"password for server");
        
       
        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = null;
        try {
			cmd = parser.parse( options, args);
		} catch (ParseException e1) {
			help(options);
		}
        
        if(cmd.hasOption("port")){
        	try{
        		port = Integer.parseInt(cmd.getOptionValue("port"));
			} catch (NumberFormatException e){
				System.out.println("-port requires a port number, parsed: "+cmd.getOptionValue("port"));
				help(options);
			}
        }

        // create a server manager and setup event handlers
        ServerManager serverManager;
        
        if(cmd.hasOption("password")) {
        	serverManager = new ServerManager(port,cmd.getOptionValue("password"));
        } else {
        	serverManager = new ServerManager(port);
        }

        /**
         * TODO: Put some server related code here.
         */

		serverManager.on(ServerManager.sessionStarted,(eventArgs)->{
			Endpoint endpoint = (Endpoint)eventArgs[0];
			log.info("Whiteboard client session started: "+endpoint.getOtherEndpointId());
			endpoint.on(shareBoard, (eventArgs2)->{
				addNewBoard((String)eventArgs2[0]);
				broadCastBoardEventToAll(sharingBoard, (String)eventArgs2[0]);
			}).on(unshareBoard, (eventArgs2)->{
				removeBoard((String)eventArgs2[0]);
				broadCastBoardEventToAll(unsharingBoard, (String)eventArgs2[0]);
			}).on(joinNetwork, (eventArgs2)-> {
				String clientAddr = (String)eventArgs2[0];
				clientAddrs.add(clientAddr);
				String boardInfo = packBoardPacket(new ArrayList<>(boardMap.keySet()));
				log.info("send shared board list to this client, " + boardInfo);
				endpoint.emit(availableBoards, boardInfo);
			});
		}).on(ServerManager.sessionStopped,(eventArgs)->{
        	Endpoint endpoint = (Endpoint)eventArgs[0];
        	log.info("Whiteboard client session ended: "+endpoint.getOtherEndpointId());
        }).on(ServerManager.sessionError, (eventArgs)->{
        	Endpoint endpoint = (Endpoint)eventArgs[0];
        	log.warning("Whiteboard client session ended in error: "+endpoint.getOtherEndpointId());
        }).on(IOThread.ioThread, (eventArgs)->{
        	String peerport = (String) eventArgs[0];
        	// we don't need this info, but let's log it
        	log.info("using Internet address: "+peerport);
		});

        
        // start up the server
        log.info("Whiteboard Server starting up");
        serverManager.start();
        // nothing more for the main thread to do
        serverManager.join();
        Utils.getInstance().cleanUp();
        
    }

}
