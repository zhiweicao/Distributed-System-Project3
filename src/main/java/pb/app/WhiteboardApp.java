package pb.app;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.logging.Logger;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import jdk.jshell.execution.Util;
import pb.WhiteboardServer;
import pb.managers.ClientManager;
import pb.managers.IOThread;
import pb.managers.PeerManager;
import pb.managers.ServerManager;
import pb.managers.endpoint.Endpoint;

import pb.utils.Utils;

/**
 * Initial code obtained from:
 * https://www.ssaurel.com/blog/learn-how-to-make-a-swing-painting-and-drawing-application/
 */
public class WhiteboardApp {
	private static Logger log = Logger.getLogger(WhiteboardApp.class.getName());
	
	/**
	 * Emitted to another peer to subscribe to updates for the given board. Argument
	 * must have format "host:port:boardid".
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String listenBoard = "BOARD_LISTEN";

	/**
	 * Emitted to another peer to unsubscribe to updates for the given board.
	 * Argument must have format "host:port:boardid".
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String unlistenBoard = "BOARD_UNLISTEN";

	/**
	 * Emitted to another peer to get the entire board data for a given board.
	 * Argument must have format "host:port:boardid".
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String getBoardData = "GET_BOARD_DATA";

	/**
	 * Emitted to another peer to give the entire board data for a given board.
	 * Argument must have format "host:port:boardid%version%PATHS".
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String boardData = "BOARD_DATA";

	/**
	 * Emitted to another peer to add a path to a board managed by that peer.
	 * Argument must have format "host:port:boardid%version%PATH". The numeric value
	 * of version must be equal to the version of the board without the PATH added,
	 * i.e. the current version of the board.
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String boardPathUpdate = "BOARD_PATH_UPDATE";

	/**
	 * Emitted to another peer to indicate a new path has been accepted. Argument
	 * must have format "host:port:boardid%version%PATH". The numeric value of
	 * version must be equal to the version of the board without the PATH added,
	 * i.e. the current version of the board.
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String boardPathAccepted = "BOARD_PATH_ACCEPTED";

	/**
	 * Emitted to another peer to remove the last path on a board managed by that
	 * peer. Argument must have format "host:port:boardid%version%". The numeric
	 * value of version must be equal to the version of the board without the undo
	 * applied, i.e. the current version of the board.
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String boardUndoUpdate = "BOARD_UNDO_UPDATE";

	/**
	 * Emitted to another peer to indicate an undo has been accepted. Argument must
	 * have format "host:port:boardid%version%". The numeric value of version must
	 * be equal to the version of the board without the undo applied, i.e. the
	 * current version of the board.
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String boardUndoAccepted = "BOARD_UNDO_ACCEPTED";

	/**
	 * Emitted to another peer to clear a board managed by that peer. Argument must
	 * have format "host:port:boardid%version%". The numeric value of version must
	 * be equal to the version of the board without the clear applied, i.e. the
	 * current version of the board.
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String boardClearUpdate = "BOARD_CLEAR_UPDATE";

	/**
	 * Emitted to another peer to indicate an clear has been accepted. Argument must
	 * have format "host:port:boardid%version%". The numeric value of version must
	 * be equal to the version of the board without the clear applied, i.e. the
	 * current version of the board.
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String boardClearAccepted = "BOARD_CLEAR_ACCEPTED";

	/**
	 * Emitted to another peer to indicate a board no longer exists and should be
	 * deleted. Argument must have format "host:port:boardid".
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String boardDeleted = "BOARD_DELETED";

	/**
	 * Emitted to another peer to indicate an error has occurred.
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String boardError = "BOARD_ERROR";
	
	/**
	 * White board map from board name to board object 
	 */
	Map<String,Whiteboard> whiteboards;
	
	/**
	 * The currently selected white board
	 */
	Whiteboard selectedBoard = null;
	
	/**
	 * The peer:port string of the peer. This is synonomous with IP:port, host:port,
	 * etc. where it may appear in comments.
	 */
	String peerport="standalone"; // a default value for the non-distributed version
	
	/*
	 * GUI objects, you probably don't need to modify these things... you don't
	 * need to modify these things... don't modify these things [LOTR reference?].
	 */
	
	JButton clearBtn, blackBtn, redBtn, createBoardBtn, deleteBoardBtn, undoBtn;
	JCheckBox sharedCheckbox ;
	DrawArea drawArea;
	JComboBox<String> boardComboBox;
	boolean modifyingComboBox=false;
	boolean modifyingCheckBox=false;
	
	/*
	 * Customize peremeters
	 */

	private PeerManager peerManager;
	private HashMap<String, ArrayList<String>> boardClientMap; 		// boardID to a list of listened client
	private ClientManager serverClient; 							// It start at the beginning and will be shutdown when program exits.

	/**
	 * Initialize the white board app.
	 */

	public WhiteboardApp(int peerPort,String whiteboardServerHost, 
			int whiteboardServerPort) {
		whiteboards=new HashMap<>();
		peerManager = new PeerManager(peerPort);
		boardClientMap = new HashMap<>();

		try {
			peerport = InetAddress.getLocalHost().getHostAddress()+":"+ String.valueOf(peerPort);
		} catch(UnknownHostException unknownHostException) {
			log.warning("Cannot access localhost address. Using 127.0.0.1 as the local host address.");

			peerport = "127.0.0.1" + ":" +String.valueOf(peerPort);
		}

		peerManager.on(PeerManager.peerServerManager, (args)->{
        	ServerManager serverManager = (ServerManager)args[0];
        	serverManager.on(IOThread.ioThread, (args2)->{
				peerport = (String) args2[0];
				joinBoardServerNetwork(peerManager, peerport, whiteboardServerPort, whiteboardServerHost);
			});
		}).on(PeerManager.peerStarted, (args)->{
        	Endpoint endpoint = (Endpoint)args[0];
        	log.info("Connection from peer: "+endpoint.getOtherEndpointId());
        	endpoint.on(WhiteboardServer.sharingBoard, (args2) -> {
				String boardID = (String)args2[0];
				addSharedBoard(boardID);
				getSharedBoardData(boardID);
			}).on(WhiteboardServer.unsharingBoard, (args2) -> {
				String boardID = (String)args2[0];
				removeSharedBoard(boardID);
			}).on(listenBoard, (args2) -> {
				String packet = (String)args2[0];
				ArrayList<String> data = WhiteboardServer.unpackPacket(packet);
				addBoardListener(data.get(0), data.get(1));
			}).on(unlistenBoard, (args2) ->{
				String packet = (String)args2[0];
				ArrayList<String> data = WhiteboardServer.unpackPacket(packet);
				removeBoardListener(data.get(0), data.get(1));
			}).on(getBoardData, (args2) ->{
				String packet = (String)args2[0];
				ArrayList<String> data = WhiteboardServer.unpackPacket(packet);
				sendBoardInitialData(data.get(0), data.get(1), endpoint);
			}).on(boardPathUpdate, (args2) -> {
				addNewPathToBoard((String)args2[0], endpoint);				
			}).on(boardUndoUpdate, (args2) -> {
				updateUndoToBoard((String)args2[0], endpoint);
			}).on(boardClearUpdate, (args2) -> {
				updateClearToBoard((String)args2[0], endpoint);
			}).on(boardDeleted, (args2) -> {

			});
        }).on(PeerManager.peerStopped,(args)->{
        	Endpoint endpoint = (Endpoint)args[0];
        	System.out.println("Disconnected from peer: "+endpoint.getOtherEndpointId());
        }).on(PeerManager.peerError,(args)->{
        	Endpoint endpoint = (Endpoint)args[0];
        	System.out.println("There was an error communicating with the peer: "
        			+endpoint.getOtherEndpointId());
        }).on(WhiteboardServer.shareBoard, (args) -> {
			// shareBoard(peerManager, (String)args[0], whiteboardServerPort, whiteboardServerHost);
			sendEventToAddr(peerManager, WhiteboardServer.shareBoard, (String)args[0], whiteboardServerPort, whiteboardServerHost);
		}).on(WhiteboardServer.unshareBoard, (args) -> {
			// unshareBoard(peerManager, (String)args[0], whiteboardServerPort, whiteboardServerHost);
			sendEventToAddr(peerManager, WhiteboardServer.unshareBoard, (String)args[0], whiteboardServerPort, whiteboardServerHost);
		}).on(listenBoard, (args) -> {
			String boardID = (String)args[1];
			String clientIP = getIP(boardID);
			int clientPort = getPort(boardID);
			sendEventToAddr(peerManager, listenBoard, (String)args[0], clientPort, clientIP);
		}).on(unlistenBoard, (args) -> {
			String boardID = (String)args[1];
			String clientIP = getIP(boardID);
			int clientPort = getPort(boardID);
			sendEventToAddr(peerManager, unlistenBoard, (String)args[0], clientPort, clientIP);
		}).on(getBoardData, (args) -> {
			runGetDataEvent(peerManager, (String)args[0]);
		}).on(boardPathUpdate, (args) -> {
			runBoardUpdateEvent(peerManager, boardPathUpdate, (String)args[0]);
		}).on(boardUndoUpdate, (args) -> {
			runBoardUpdateEvent(peerManager, boardUndoUpdate, (String)args[0]);
		}).on(boardClearUpdate, (args) -> {
			runBoardUpdateEvent(peerManager, boardClearUpdate, (String)args[0]);
		}).on(boardDeleted, (args) -> {
			String boardID = (String)args[0];
			if (getIPWithPort(boardID).equals(peerport)) {
				peerManager.emit(WhiteboardServer.unshareBoard, boardID);
			} else {
				String packet = WhiteboardServer.packPacket(new ArrayList<>(Arrays.asList(boardID, peerport)));
				log.info("emit unlistenBoard event");
				peerManager.emit(unlistenBoard, packet, boardID);
			}
		}).on(WhiteboardServer.leaveNetwork, (args) -> {
			sendEventToAddr(peerManager, WhiteboardServer.leaveNetwork, peerport, whiteboardServerPort, whiteboardServerHost);
		});
		peerManager.start();

		show(peerport);
	}
	
	/******
	 * 
	 * Utility methods to extract fields from argument strings.
	 * 
	 ******/
	
	/**
	 * 
	 * @param data = peer:port:boardid%version%PATHS
	 * @return peer:port:boardid
	 */
	public static String getBoardName(String data) {
		String[] parts=data.split("%",2);
		return parts[0];
	}
	
	/**
	 * 
	 * @param data = peer:port:boardid%version%PATHS
	 * @return boardid%version%PATHS
	 */
	public static String getBoardIdAndData(String data) {
		String[] parts=data.split(":");
		return parts[2];
	}
	
	/**
	 * 
	 * @param data = peer:port:boardid%version%PATHS
	 * @return version%PATHS
	 */
	public static String getBoardData(String data) {
		String[] parts=data.split("%",2);
		return parts[1];
	}
	
	/**
	 * 
	 * @param data = peer:port:boardid%version%PATHS
	 * @return version
	 */
	public static long getBoardVersion(String data) {
		String[] parts=data.split("%",3);
		return Long.parseLong(parts[1]);
	}
	
	/**
	 * 
	 * @param data = peer:port:boardid%version%PATHS
	 * @return PATHS
	 */
	public static String getBoardPaths(String data) {
		String[] parts=data.split("%",3);
		return parts[2];
	}
	
	/**
	 * 
	 * @param data = peer:port:boardid%version%PATHS
	 * @return peer
	 */
	public static String getIP(String data) {
		String[] parts=data.split(":");
		return parts[0];
	}
	
	/**
	 * 
	 * @param data = peer:port:boardid%version%PATHS
	 * @return port
	 */
	public static int getPort(String data) {
		String[] parts=data.split(":");
		return Integer.parseInt(parts[1]);
	}
	
	public static String getIPWithPort(String data) {
		String[] parts=data.split(":");
		return parts[0] + ":" + parts[1];
	}

	private static boolean isBoardOwnerEqualToAddr(String addr, String boardID) {
		String boardOwner = getIPWithPort(boardID);
		if (addr.equals(boardOwner)) {
			return true;
		}
		return false;
	}

	/******
	 * 
	 * Methods called from events.
	 * 
	 ******/
	

	/**
	 * The board server methods
	 */
	/**
	 * Run this method in the setup stage to get all shared boards in the network.
	 * @param peerManager
	 * @param peerport
	 * @param whiteboardServerPort
	 * @param whiteboardServerHost
	 */
	private void joinBoardServerNetwork(PeerManager peerManager, String peerport, int whiteboardServerPort, String whiteboardServerHost) {
		try {
			ClientManager clientManager = peerManager.connect(whiteboardServerPort, whiteboardServerHost);
			serverClient = clientManager;

			clientManager.on(PeerManager.peerStarted, (args3) -> {
				Endpoint endpoint = (Endpoint)args3[0];
				log.info("Connected to whiteboard server: "+endpoint.getOtherEndpointId());

				endpoint.on(WhiteboardServer.availableBoards, (args4) -> {
					String boardInfo = (String)args4[0];
					log.info("Receive board list from server. Board info is: " + boardInfo);
					updateBroadList(boardInfo);
					// Shutdown this clientManager when GUI is shutdown. It will maintain timer working.
					// clientManager.shutdown();
				});

				endpoint.emit(WhiteboardServer.joinNetwork, peerport);
			}).on(PeerManager.peerStopped, (args3)->{
				Endpoint endpoint = (Endpoint)args3[0];
				log.info("Disconnected from peer: "+endpoint.getOtherEndpointId());
				clientManager.shutdown();
			}).on(PeerManager.peerError, (args3)->{
				Endpoint endpoint = (Endpoint)args3[0];
				log.info("There was error while communication with peer: "
						+endpoint.getOtherEndpointId());
				clientManager.shutdown();
			});

			clientManager.start();
		} catch (UnknownHostException e) {
			log.info("The whiteboard server host could not be found: "+ whiteboardServerHost);
		} catch (InterruptedException e) {
			log.info("Interrupted while trying to send updates to the index whiteboard");
		}
	}

	/**
	 * run this method at the initial stage to receice all shared board id and require board data from owners.
	 * @param boardInfo
	 */
	private void updateBroadList(String boardInfo) {
		if (boardInfo.length() == 0) return;
		ArrayList<String> boardList = WhiteboardServer.unpackPacket(boardInfo);

		for (String boardID : boardList) {
			log.info("attempt to add share boardID: " + boardID + " to boardList in setup stage.");
			Whiteboard whiteboard = new Whiteboard(boardID, true);
			addBoardBeforeSetUp(whiteboard);
			if (!isBoardOwnerEqualToAddr(peerport, boardID)) {
				getSharedBoardData(boardID);
			}
		}
	}

	/**
	 * A general method to send an event with arguemnt to a receiver.
	 */
	private void sendEventToAddr(PeerManager peerManager, String event, Object arg, int port, String host) {
		log.info("Whiteboard " + event + " event started. arg: " + arg);

		try {
			ClientManager clientManager = peerManager.connect(port, host);
			clientManager.on(PeerManager.peerStarted, (args) -> {
				Endpoint endpoint = (Endpoint)args[0];
				log.info("Connected to whiteboard server: "+endpoint.getOtherEndpointId());
				endpoint.emit(event, arg);
				clientManager.shutdown();
			}).on(PeerManager.peerStopped, (args)->{
				Endpoint endpoint = (Endpoint)args[0];
				log.info("Disconnected from peer: "+endpoint.getOtherEndpointId());
				clientManager.shutdown();
			}).on(PeerManager.peerError, (args)->{
				Endpoint endpoint = (Endpoint)args[0];
				log.info("There was error while communication with peer: "
						+endpoint.getOtherEndpointId());
				clientManager.shutdown();
			});
			clientManager.start();
		} catch (UnknownHostException e) {
			log.info("The whiteboard server host could not be found: "+ host);
		} catch (InterruptedException e) {
			log.info("Interrupted while trying to share boardID to the whiteboard server");
		}
	}

	/**
	 * Add shared board into combo box
	 */
	private void addSharedBoard(String boardID) {
		log.info("attempt to add share board: " + boardID + " to boardList");
		Whiteboard whiteboard = new Whiteboard(boardID, true);
		addBoard(whiteboard, false);
	}

	/**
	 * get shared board data from board owner.
	 */
	private void getSharedBoardData(String boardID) {
		log.info("Requesting shared board.");
		String packet = WhiteboardServer.packPacket(new ArrayList<>(Arrays.asList(boardID, peerport)));
		peerManager.emit(listenBoard, packet, boardID);
		peerManager.emit(getBoardData, packet);
	}
	
	/**
	 * remove shared board from combo box
	 */
	private void removeSharedBoard(String boardID) {
		log.info("attempt to remove share board: " + boardID + " to boardList");

		synchronized(whiteboards) {
			Whiteboard whiteboard = whiteboards.get(boardID);
			if(whiteboard!=null) {
				whiteboards.remove(boardID);
			}
		}

		updateComboBox(null);
	}	

	/**
	 * Add board listener to own's boardID map
	 */
	private void addBoardListener(String boardID, String clientAddr) {
		if (isBoardOwnerEqualToAddr(clientAddr, boardID)) {
			log.warning("board "+ boardID +" listener is the board owner. client address is " + clientAddr);
			return;
		}
		
		ArrayList<String> clients;
		if (!boardClientMap.containsKey(boardID)) {
			clients = new ArrayList<>();
		} else {
			clients = boardClientMap.get(boardID);
		}

		clients.add(clientAddr);
		log.info("add client: " + clientAddr + " to board "+ boardID +" listener list.");
		boardClientMap.put(boardID, clients);
	}

	/**
	 * remove board listener to own's boardID map
	 */
	private void removeBoardListener(String boardID, String clientAddr) {
		ArrayList<String> clients;
		if (!boardClientMap.containsKey(boardID)) return;

		clients = boardClientMap.get(boardID);
		clients.remove(clientAddr);
		log.info("remove client: " + clientAddr + " from board "+ boardID +"listener list.");
		boardClientMap.put(boardID, clients);
	}	
	
	/**
	 * reply the getBoardData request and send board data back.
	 */
	private void sendBoardInitialData(String boardID, String clientAddr, Endpoint endpoint) {
		if (!whiteboards.containsKey(boardID)) return;

		String data;
		synchronized(whiteboards) {
			Whiteboard whiteboard = whiteboards.get(boardID);
			data = whiteboard.toString();
		}
		log.info("send board data: " + boardID + " to :" + clientAddr);
		endpoint.emit(boardData, data);
	}

	/**
	 *  draw board based on the received data
	 */
	private void drawBoardInitialData(String data) {
		String boardID = getBoardName(data);
		String boardData = getBoardData(data);

		if (!whiteboards.containsKey(boardID)) return;

		Whiteboard whiteboard;
		synchronized(whiteboards) {
			whiteboard = whiteboards.get(boardID);
		}
		whiteboard.whiteboardFromString(boardID, boardData);
	}

	/**
	 * emit getBoardData to board owner. The client will be shutdown after receive boardData.
	 */
	private void runGetDataEvent(PeerManager peerManager, String boardID) {
		String clientIP = getIP(boardID);
		int clientPort = getPort(boardID);

		log.info("Whiteboard getData event started.");

		try {
			ClientManager clientManager = peerManager.connect(clientPort, clientIP);
			clientManager.on(PeerManager.peerStarted, (args) -> {
				Endpoint endpoint = (Endpoint)args[0];
				log.info("Connected to whiteboard server: "+endpoint.getOtherEndpointId());
				
				endpoint.on(boardData, (args2) -> {
					String data = (String)args2[0];
					drawBoardInitialData(data);
					clientManager.shutdown();
				});
				endpoint.emit(getBoardData, boardID);
   			}).on(PeerManager.peerStopped, (args)->{
				Endpoint endpoint = (Endpoint)args[0];
				log.info("Disconnected from peer: "+endpoint.getOtherEndpointId());
				clientManager.shutdown();
			}).on(PeerManager.peerError, (args)->{
				Endpoint endpoint = (Endpoint)args[0];
				log.info("There was error while communication with peer: "
						+endpoint.getOtherEndpointId());
				clientManager.shutdown();
			});
			clientManager.start();
		} catch (UnknownHostException e) {
			log.info("The whiteboard server host could not be found: "+ clientIP);
		} catch (InterruptedException e) {
			log.info("Interrupted while trying to share boardID to the whiteboard server");
		}
	}
	
	/**
	 * Run boardupdate event, will emit path, undo, or clear event
	 * if owner run this function. it will broadcast change to all listener.
	 * otherwise, this change will send to board owner to get accept.
	 * @param peerManager
	 * @param event
	 * @param commit
	 */
	private void runBoardUpdateEvent(PeerManager peerManager, String event, String commit) {
		String owner = getIPWithPort(commit);
		
		if (owner.equals(peerport)) {							// owner boardcast this path to all listener.
			sendEventToBoardListeners(peerManager, event, commit);
		} else {												// send path to board owner.
			String ownerIP = getIP(owner);
			int ownerPort = getPort(owner);

			log.info("Start to update new commit to board owner.");
	
			try {
				ClientManager clientManager = peerManager.connect(ownerPort, ownerIP);
				clientManager.on(PeerManager.peerStarted, (args) -> {
					Endpoint endpoint = (Endpoint)args[0];
					log.info("Connected to whiteboard server: "+endpoint.getOtherEndpointId());
					
					//TODO: handle unaccpeted cases.
					if (event.equals(boardPathUpdate)) {
						endpoint.on(boardPathAccepted, (args2) -> {
							clientManager.shutdown();
						});
						endpoint.emit(boardPathUpdate, commit);
					} else if (event.equals(boardUndoUpdate)) {
						endpoint.on(boardUndoAccepted, (args2) -> {
							clientManager.shutdown();
						});
						endpoint.emit(boardUndoUpdate, commit);
					} else if (event.equals(boardClearUpdate)) {
						endpoint.on(boardClearAccepted, (args2) -> {
							clientManager.shutdown();
						});
						endpoint.emit(boardClearUpdate, commit);
					}

				   }).on(PeerManager.peerStopped, (args)->{
					Endpoint endpoint = (Endpoint)args[0];
					log.info("Disconnected from peer: "+endpoint.getOtherEndpointId());
					clientManager.shutdown();
				}).on(PeerManager.peerError, (args)->{
					Endpoint endpoint = (Endpoint)args[0];
					log.info("There was error while communication with peer: "
							+endpoint.getOtherEndpointId());
					clientManager.shutdown();
				});
				clientManager.start();
			} catch (UnknownHostException e) {
				log.info("The whiteboard server host could not be found: "+ ownerIP);
			} catch (InterruptedException e) {
				log.info("Interrupted while trying to share boardID to the whiteboard server");
			}	

			
		}
	}

	/**
	 * send action event to all of this board listener.
	 * @param peerManager
	 * @param event
	 * @param commit
	 */
	private void sendEventToBoardListeners(PeerManager peerManager, String event, String commit) {
		String boardID = getBoardName(commit);
		if (!boardClientMap.containsKey(boardID)) return;
		ArrayList<String> allClientAddr = boardClientMap.get(boardID);

		for(String clientAddr: allClientAddr) {
			log.info("Send board commit to board listener: " + clientAddr + ". Commit: " + commit);
			String clientHost = getIP(clientAddr);
			int clientPort = getPort(clientAddr);
			sendEventToAddr(peerManager, event, commit, clientPort, clientHost);
		}
	}

	/**
	 * add updated path to listened board.
	 * the owner will boardcast this change to all listener.
	 * @param commit
	 * @param endpoint
	 */
	private void addNewPathToBoard(String commit, Endpoint endpoint) {
		String boardID = getBoardName(commit);
		String owner = getIPWithPort(boardID);
		long version = getBoardVersion(commit);
		String newPath = getBoardPaths(commit);

		if (!whiteboards.containsKey(boardID)) {
			log.warning("Cannot update whiteboard " + boardID + ". Because it is not found in the boardlist.");
			return;
		}

		Whiteboard whiteboard = whiteboards.get(boardID);

		if (whiteboard.addPath(new WhiteboardPath(newPath), version-1)) {
			if (peerport.equals(owner)) {
				log.info("Path change has been added into board: " + boardID + ". The path data are: " + newPath.toString());
				endpoint.emit(boardPathAccepted, "");
				sendEventToBoardListeners(peerManager, boardPathUpdate, commit);
			} else {
				log.info("Accpet change from board owner: " + boardID + ". The path data are: " + newPath.toString());
			}
			drawSelectedWhiteboard(); 
		} else {
			// ignore the boardcast from board owner. TODO: use a better way to avoid conflict case
			if (!(version == whiteboard.getVersion())){
				log.warning("Cannot update whiteboard " + boardID + ". The issue occurred during addPath. "  + newPath.toString());
			}
		}
	}

	/**
	 * add updated undo to listened board.
	 * the owner will boardcast this change to all listener.
	 * @param commit
	 * @param endpoint
	 */
	private void updateUndoToBoard(String commit, Endpoint endpoint) {
		String boardID = getBoardName(commit);
		String owner = getIPWithPort(boardID);
		long version = getBoardVersion(commit);

		if (!whiteboards.containsKey(boardID)) {
			log.warning("Cannot update whiteboard " + boardID + ". Because it is not found in the boardlist.");
			return;
		}

		Whiteboard whiteboard = whiteboards.get(boardID);

		if (whiteboard.undo(version-1)) {
			if (peerport.equals(owner)) {
				log.info("Undo change has been made into board: " + boardID + ". The Undo version are: " + version);
				endpoint.emit(boardUndoAccepted, "");
				sendEventToBoardListeners(peerManager, boardUndoUpdate, commit);
			} else {
				log.info("Accpet change from board owner: " + boardID + ". The Undo version are: " + version);
			}
			drawSelectedWhiteboard(); 
		} else {
			// ignore the boardcast from board owner. TODO: use a better way to avoid conflict case
			if (!(version == whiteboard.getVersion())){
				log.warning("Cannot update whiteboard " + boardID + ". The issue occurred during undo action. undo version is: "  + version);
			}
		}
	}

	/**
	 * make clear to listened board.
	 * the owner will boardcast this change to all listener.
	 */
	private void updateClearToBoard(String commit, Endpoint endpoint) {
		String boardID = getBoardName(commit);
		String owner = getIPWithPort(boardID);
		long version = getBoardVersion(commit);

		if (!whiteboards.containsKey(boardID)) {
			log.warning("Cannot update whiteboard " + boardID + ". Because it is not found in the boardlist.");
			return;
		}

		Whiteboard whiteboard = whiteboards.get(boardID);

		if (whiteboard.clear(version-1)) {
			if (peerport.equals(owner)) {
				log.info("Clear change has been made into board: " + boardID + ". The clear version are: " + version);
				endpoint.emit(boardClearAccepted, "");
				sendEventToBoardListeners(peerManager, boardClearUpdate, commit);
			} else {
				log.info("Accpet change from board owner: " + boardID + ". The clear version are: " + version);
			}
			drawSelectedWhiteboard(); 
		} else {
			// ignore the boardcast from board owner. TODO: use a better way to avoid conflict case
			if (!(version == whiteboard.getVersion())){
				log.warning("Cannot update whiteboard " + boardID + ". The error occurred during clear action. Clear version is: "  + version + ". whiteboard version is: " + whiteboard.getVersion());
			}
		}
	}
	

	/******
	 * 
	 * Methods to manipulate data locally. Distributed systems related code has been
	 * cut from these methods.
	 * 
	 ******/
	
	/**
	 * Wait for the peer manager to finish all threads.
	 */
	public void waitToFinish() {
        peerManager.joinWithClientManagers();
	}
	
	/**
	 * Add a board to the list that the user can select from. If select is
	 * true then also select this board.
	 * @param whiteboard
	 * @param select
	 */
	public void addBoard(Whiteboard whiteboard,boolean select) {
		synchronized(whiteboards) {
			whiteboards.put(whiteboard.getName(), whiteboard);
		}
		updateComboBox(select?whiteboard.getName():null);
	}
	
	/**
	 * Add a board before setup. Avoid update combo box.
	 */
	public void addBoardBeforeSetUp(Whiteboard whiteboard) {
		synchronized(whiteboards) {
			whiteboards.put(whiteboard.getName(), whiteboard);
		}
	}
	

	/**
	 * Delete a board from the list.
	 * @param boardname must have the form peer:port:boardid
	 */
	public void deleteBoard(String boardname) {
		synchronized(whiteboards) {
			Whiteboard whiteboard = whiteboards.get(boardname);
			if(whiteboard!=null) {
				whiteboards.remove(boardname);
			}
		}
		updateComboBox(null);

		peerManager.emit(boardDeleted, boardname);
	}
	
	/**
	 * Create a new local board with name peer:port:boardid.
	 * The boardid includes the time stamp that the board was created at.
	 */
	public void createBoard() {
		String name = peerport+":board"+Instant.now().toEpochMilli();
		Whiteboard whiteboard = new Whiteboard(name,false);
		addBoard(whiteboard,true);
		// peerManager.emit(WhiteboardServer.shareBoard, name);
	}
	
	/**
	 * Add a path to the selected board. The path has already
	 * been drawn on the draw area; so if it can't be accepted then
	 * the board needs to be redrawn without it.
	 * @param currentPath
	 */
	public void pathCreatedLocally(WhiteboardPath currentPath) {
		if(selectedBoard!=null) {
			if(!selectedBoard.addPath(currentPath,selectedBoard.getVersion())) {
				// some other peer modified the board in between
				drawSelectedWhiteboard(); // just redraw the screen without the path
			} else {
				// was accepted locally, so do remote stuff if needed
				String commit = selectedBoard.getNameAndVersion() + "%" + currentPath.toString();
				peerManager.emit(boardPathUpdate, commit);			
			}
		} else {
			log.severe("path created without a selected board: "+currentPath);
		}
	}
	
	/**
	 * Clear the selected whiteboard.
	 */
	public void clearedLocally() {
		if(selectedBoard!=null) {
			if(!selectedBoard.clear(selectedBoard.getVersion())) {
				// some other peer modified the board in between
				drawSelectedWhiteboard();
			} else {
				// was accepted locally, so do remote stuff if needed
				String commit = selectedBoard.getNameAndVersion();
				peerManager.emit(boardClearUpdate, commit);
				drawSelectedWhiteboard();
			}
		} else {
			log.severe("cleared without a selected board");
		}
	}
	
	/**
	 * Undo the last path of the selected whiteboard.
	 */
	public void undoLocally() {
		if(selectedBoard!=null) {
			if(!selectedBoard.undo(selectedBoard.getVersion())) {
				// some other peer modified the board in between
				drawSelectedWhiteboard();
			} else {
				String commit = selectedBoard.getNameAndVersion();
				peerManager.emit(boardUndoUpdate, commit);
				drawSelectedWhiteboard();
			}
		} else {
			log.severe("undo without a selected board");
		}
	}
	
	/**
	 * The variable selectedBoard has been set.
	 */
	public void selectedABoard() {
		drawSelectedWhiteboard();
		log.info("selected board: "+selectedBoard.getName());
	}
	
	/**
	 * Set the share status on the selected board.
	 */
	public void setShare(boolean share) {
		if(selectedBoard!=null) {
			selectedBoard.setShared(share);
			if (share == true) {
				peerManager.emit(WhiteboardServer.shareBoard, selectedBoard.getName());
			} else {
				peerManager.emit(WhiteboardServer.unshareBoard, selectedBoard.getName());
			}
        } else {
        	log.severe("there is no selected board");
        }
	}
	
	/**
	 * Called by the gui when the user closes the app.
	 */
	public void guiShutdown() {
		// do some final cleanup
		HashSet<Whiteboard> existingBoards= new HashSet<>(whiteboards.values());
		existingBoards.forEach((board)->{
			deleteBoard(board.getName());
		});
    	whiteboards.values().forEach((whiteboard)->{
    		
		});
		peerManager.emit(WhiteboardServer.leaveNetwork, "");
		Utils.getInstance().setTimeout(() -> {
			serverClient.shutdown();
		}, 1000);
	}
	
	

	/******
	 * 
	 * GUI methods and callbacks from GUI for user actions.
	 * You probably do not need to modify anything below here.
	 * 
	 ******/
	
	/**
	 * Redraw the screen with the selected board
	 */
	public void drawSelectedWhiteboard() {
		drawArea.clear();
		if(selectedBoard!=null) {
			selectedBoard.draw(drawArea);
		}
	}
	
	/**
	 * Setup the Swing components and start the Swing thread, given the
	 * peer' specific information, i.e. peer:port string.
	 */
	public void show(String peerport) {
		// create main frame
		JFrame frame = new JFrame("Whiteboard Peer: "+peerport);
		Container content = frame.getContentPane();
		// set layout on content pane
		content.setLayout(new BorderLayout());
		// create draw area
		drawArea = new DrawArea(this);

		// add to content pane
		content.add(drawArea, BorderLayout.CENTER);

		// create controls to apply colors and call clear feature
		JPanel controls = new JPanel();
		controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));

		/**
		 * Action listener is called by the GUI thread.
		 */
		ActionListener actionListener = new ActionListener() {

			public void actionPerformed(ActionEvent e) {
				if (e.getSource() == clearBtn) {
					clearedLocally();
				} else if (e.getSource() == blackBtn) {
					drawArea.setColor(Color.black);
				} else if (e.getSource() == redBtn) {
					drawArea.setColor(Color.red);
				} else if (e.getSource() == boardComboBox) {
					if(modifyingComboBox) return;
					if(boardComboBox.getSelectedIndex()==-1) return;
					String selectedBoardName=(String) boardComboBox.getSelectedItem();
					if(whiteboards.get(selectedBoardName)==null) {
						log.severe("selected a board that does not exist: "+selectedBoardName);
						return;
					}
					selectedBoard = whiteboards.get(selectedBoardName);
					// remote boards can't have their shared status modified
					if(selectedBoard.isRemote()) {
						sharedCheckbox.setEnabled(false);
						sharedCheckbox.setVisible(false);
					} else {
						modifyingCheckBox=true;
						sharedCheckbox.setSelected(selectedBoard.isShared());
						modifyingCheckBox=false;
						sharedCheckbox.setEnabled(true);
						sharedCheckbox.setVisible(true);
					}
					selectedABoard();
				} else if (e.getSource() == createBoardBtn) {
					createBoard();
				} else if (e.getSource() == undoBtn) {
					if(selectedBoard==null) {
						log.severe("there is no selected board to undo");
						return;
					}
					undoLocally();
				} else if (e.getSource() == deleteBoardBtn) {
					if(selectedBoard==null) {
						log.severe("there is no selected board to delete");
						return;
					}
					deleteBoard(selectedBoard.getName());
				}
			}
		};
		
		clearBtn = new JButton("Clear Board");
		clearBtn.addActionListener(actionListener);
		clearBtn.setToolTipText("Clear the current board - clears remote copies as well");
		clearBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
		blackBtn = new JButton("Black");
		blackBtn.addActionListener(actionListener);
		blackBtn.setToolTipText("Draw with black pen");
		blackBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
		redBtn = new JButton("Red");
		redBtn.addActionListener(actionListener);
		redBtn.setToolTipText("Draw with red pen");
		redBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
		deleteBoardBtn = new JButton("Delete Board");
		deleteBoardBtn.addActionListener(actionListener);
		deleteBoardBtn.setToolTipText("Delete the current board - only deletes the board locally");
		deleteBoardBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
		createBoardBtn = new JButton("New Board");
		createBoardBtn.addActionListener(actionListener);
		createBoardBtn.setToolTipText("Create a new board - creates it locally and not shared by default");
		createBoardBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
		undoBtn = new JButton("Undo");
		undoBtn.addActionListener(actionListener);
		undoBtn.setToolTipText("Remove the last path drawn on the board - triggers an undo on remote copies as well");
		undoBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
		sharedCheckbox = new JCheckBox("Shared");
		sharedCheckbox.addItemListener(new ItemListener() {    
	         public void itemStateChanged(ItemEvent e) { 
	            if(!modifyingCheckBox) setShare(e.getStateChange()==1);
	         }    
	      }); 
		sharedCheckbox.setToolTipText("Toggle whether the board is shared or not - tells the whiteboard server");
		sharedCheckbox.setAlignmentX(Component.CENTER_ALIGNMENT);
		

		// create a drop list for boards to select from
		JPanel controlsNorth = new JPanel();
		boardComboBox = new JComboBox<String>();
		boardComboBox.addActionListener(actionListener);
		
		
		// add to panel
		controlsNorth.add(boardComboBox);
		controls.add(sharedCheckbox);
		controls.add(createBoardBtn);
		controls.add(deleteBoardBtn);
		controls.add(blackBtn);
		controls.add(redBtn);
		controls.add(undoBtn);
		controls.add(clearBtn);

		// add to content pane
		content.add(controls, BorderLayout.WEST);
		content.add(controlsNorth,BorderLayout.NORTH);

		frame.setSize(600, 600);
		
		// create an initial board
		createBoard();
		
		// closing the application
		frame.addWindowListener(new WindowAdapter() {
		    @Override
		    public void windowClosing(WindowEvent windowEvent) {
		        if (JOptionPane.showConfirmDialog(frame, 
		            "Are you sure you want to close this window?", "Close Window?", 
		            JOptionPane.YES_NO_OPTION,
		            JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION)
		        {
		        	guiShutdown();
		            frame.dispose();
		        }
		    }
		});
		
		// show the swing paint result
		frame.setVisible(true);
		
	}
	
	/**
	 * Update the GUI's list of boards. Note that this method needs to update data
	 * that the GUI is using, which should only be done on the GUI's thread, which
	 * is why invoke later is used.
	 * 
	 * @param select, board to select when list is modified or null for default
	 *                selection
	 */
	private void updateComboBox(String select) {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				modifyingComboBox=true;
				boardComboBox.removeAllItems();
				int anIndex=-1;
				synchronized(whiteboards) {
					ArrayList<String> boards = new ArrayList<String>(whiteboards.keySet());
					Collections.sort(boards);
					for(int i=0;i<boards.size();i++) {
						String boardname=boards.get(i);
						boardComboBox.addItem(boardname);
						if(select!=null && select.equals(boardname)) {
							anIndex=i;
						} else if(anIndex==-1 && selectedBoard!=null && 
								selectedBoard.getName().equals(boardname)) {
							anIndex=i;
						} 
					}
				}
				modifyingComboBox=false;
				if(anIndex!=-1) {
					boardComboBox.setSelectedIndex(anIndex);
				} else {
					if(whiteboards.size()>0) {
						boardComboBox.setSelectedIndex(0);
					} else {
						drawArea.clear();
						createBoard();
					}
				}
				
			}
		});
	}
	
}
