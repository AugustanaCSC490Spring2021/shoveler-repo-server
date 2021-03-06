/**
 * This program is the server manager for the Shoveler game project as a part
 * of the Augustana CSC-490 Senior Project Spring 2021. You can find more information
 * by using the following links.
 * 
 *  https://github.com/AugustanaCSC490Spring2021/shoveler-repo-server
 *  https://github.com/AugustanaCSC490Spring2021/shoveler-repo
 *  
 *  The code for sending and receiving data between C# and Java was borrowed by
 *  damix911 from Stackoverflow. From what I understand, the code attaches the message
 *  length to the beginning of the message. Then, the otherside makes sure to grab the 
 *  whole message before continuing. Thanks damix! 
 *  https://stackoverflow.com/questions/14824491/can-i-communicate-between-java-and-c-sharp-using-just-sockets/14825061
 *  
 * @author Landen
 *
 */

package org.augie;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Random;
import org.json.*;

public class Server {
	public static void main(String[] args) throws IOException {
		// Here we create a server socket listening on the provided port
		if (args.length != 1) {
            System.err.println("Usage: java Server <port number>");
            System.exit(1);
        }
		
		int portNumber = Integer.parseInt(args[0]);
		
		ArrayList<Player> players = new ArrayList<Player>();
		Hashtable<String, Player> lobbyTable = new Hashtable<String, Player>();
        ServerSocket serverSocket = new ServerSocket(portNumber);
        
        /**
         * This is the main loop for the game server. Inside this loop, we wait
         * for a new connection to reach the server, we then respond with the 
         * appropriate response via the switch statement below. After the message
         * is sent, we close the client socket and restart the loop, waiting for
         * a new connection.
         */
        while(true) {
        	// .accept() is a blocking method.
	        Socket socket = serverSocket.accept();
	        InputStream is = socket.getInputStream();
	        OutputStream os = socket.getOutputStream();
	
	        // Receiving
	        byte[] lenBytes = new byte[4];
	        is.read(lenBytes, 0, 4);
	        int len = (((lenBytes[3] & 0xff) << 24) | ((lenBytes[2] & 0xff) << 16) |
	                  ((lenBytes[1] & 0xff) << 8) | (lenBytes[0] & 0xff));
	        byte[] receivedBytes = new byte[len];
	        is.read(receivedBytes, 0, len);
	        String received = new String(receivedBytes, 0, len);
	
	        System.out.println("Server received: " + received);
	        
	        // If we are unable to find a proper action via the switch statement, 
	        // we default to a "bad" response.
	        JSONObject jsonObject = new JSONObject(received);
	        String JSONReply = new JSONObject().put("response", "bad").toString();
					
	        long id;
	        String roomCode;
	        int seed = 0;
	        boolean doesRoomExist = false;
	        
	        /**
	         * Each request send by the client is in a JSON format. There is
	         * always an "action" value that is sent. Here we use a switch statement
	         * to determine what data needs to be saved/sent back to the player.
	         */
	        switch( jsonObject.getString("action") ) {
	        /**
	         * When creating a lobby we do a couple things. First, we create a new
	         * Player object for the host. Next, we create a new room code that
	         * the other player can use to connect. Finally, we send the room code
	         * back to the host player. 
	         */
	        case "NewLobby":
	        	id = new Random().nextLong() & Long.MAX_VALUE;
	        	
	        	// Make sure random room code does not already exist
	        	roomCode = String.valueOf(new Random().nextInt(9999));
	        	Player selectedLobby = lobbyTable.get(roomCode);
	        	while(selectedLobby != null) {
	        		if(selectedLobby.getTime() > 0 && selectedLobby.getClientTime() > 0) {
	        			lobbyTable.remove(roomCode);
	        		}else {
	        			roomCode = String.valueOf(new Random().nextInt(9999));
	        		}
	        	}
	        	
	        	Player newPlayer = new Player(
	        			id, 
						jsonObject.getString("name"), 
						true, 
						jsonObject.getInt("seed"),
						jsonObject.getInt("difficulty"),
						roomCode);
	        	
	        	players.add(newPlayer);
	        	lobbyTable.put(roomCode, newPlayer);
	        	
	        	JSONReply = new JSONObject()
	    						.put("response", "OK")
	    						.put("id", id)
	    						.put("roomCode", roomCode)
	    						.toString();
	        	break;
	        /**
	         * Join room 
	         */
	        case "JoinRoom":
	        	// Connect a player to a room
	        	id = new Random().nextLong() & Long.MAX_VALUE;
	        	roomCode = jsonObject.getString("roomCode");
	        	
	        	doesRoomExist = false;
        		if (lobbyTable.get(roomCode) != null) {
        			doesRoomExist = true;
        		}
	        	
	        	if (doesRoomExist) {
	        		Player host = lobbyTable.get(roomCode);
	        		host.startGame();
	        		host.setClient(new Player(
		        			id, 
							jsonObject.getString("name"), 
							false, 
							host.getSeed(),
							host.getDifficulty(),
							roomCode
							));
	        		
		        	players.add(host.getClient());
		        	
		        	JSONReply = new JSONObject()
							.put("response", "OK")
							.put("id", id)
							.put("seed", host.getSeed())
							.put("difficulty", host.getDifficulty())
							.put("enemyName", host.getName())
							.toString();
		        	
	        	} else {
	        		JSONReply = new JSONObject()
							.put("response", "Missing")
							.put("id", 0)
							.put("seed", 0)
							.toString();
	        	}
	        	break;
	        case "ShouldStart":
	        	roomCode = jsonObject.getString("roomCode");
	        	
	        	Player host = lobbyTable.get(roomCode);
        		if (host != null) {
        			if (lobbyTable.get(roomCode).shouldStart()) {
        				JSONReply = new JSONObject()
								.put("response", "Yes")
								.toString();
        			}else {
        				JSONReply = new JSONObject()
								.put("response", "No")
								.toString();
        			}
        		}
	        	break;
	        case "SubmitScore":
	        	String name = jsonObject.getString("name");
	        	boolean isHost = jsonObject.getBoolean("isHost");
	        	id = jsonObject.getLong("id");
	        	long time = jsonObject.getLong("time");
	        	long score = jsonObject.getLong("score");
	        	roomCode = jsonObject.getString("roomCode");
	        	boolean hasDied = jsonObject.getBoolean("hasDied");
	        	
	        	Player getHost = lobbyTable.get(roomCode);
	        	if (getHost != null) {
	        		// Determine if the submitter is host and apply their time/score to
	        		// the host's object.
		        	if (isHost) {
		        		getHost.setTime(time);
		        		getHost.setScore(score);
		        		getHost.setHasDied(hasDied);
		        		
		        		// Check if other player is finished yet
		        		if (getHost.getClientTime() == 0) {
		        			JSONReply = new JSONObject()
									.put("response", "NotDone")
									.toString();
		        		}else {
		        			JSONReply = new JSONObject()
									.put("response", "Done")
									.put("enemyTime", getHost.getClientTime())
									.put("enemyScore", getHost.getClientScore())
									.put("enemyDeath", getHost.getClientDeath())
									.put("enemyName", getHost.getClient().getName())
									.toString();
		        		}
		        	// If not Host
		        	}else {
		        		getHost.setClientTime(time);
		        		getHost.setClientScore(score);
		        		getHost.setClientDeath(hasDied);
		        		
		        		// Check if other player is finished yet
		        		if (getHost.getTime() == 0) {
		        			JSONReply = new JSONObject()
									.put("response", "NotDone")
									.toString();
		        		}else {
		        			JSONReply = new JSONObject()
									.put("response", "Done")
									.put("enemyTime", getHost.getTime())
									.put("enemyScore", getHost.getScore())
									.put("enemyDeath", getHost.getIfHasDied())
									.put("enemyName", getHost.getName())
									.toString();
		        		}
		        	}
	        	}
	        	break;
	        }
	        
	     // Sending
	        String toSend = JSONReply;
	        byte[] toSendBytes = toSend.getBytes();
	        int toSendLen = toSendBytes.length;
	        byte[] toSendLenBytes = new byte[4];
	        toSendLenBytes[0] = (byte)(toSendLen & 0xff);
	        toSendLenBytes[1] = (byte)((toSendLen >> 8) & 0xff);
	        toSendLenBytes[2] = (byte)((toSendLen >> 16) & 0xff);
	        toSendLenBytes[3] = (byte)((toSendLen >> 24) & 0xff);
	        os.write(toSendLenBytes);
	        os.write(toSendBytes);
	        
	        socket.close();
        }
	}
}

/**
 * This is a utility class for tracking new player data. If a player is marked
 * as a host, they will manage all important variables relating to the lobby such as
 * seed, difficulty, etc.
 * @author Landen
 *
 */
class Player {
	private long id;
	private String name;
	private boolean isHost = false;
	private int seed;
	private int difficulty;
	private String roomCode;
	private long score;
	private long time = 0;
	private boolean hasStarted = false;
	private boolean hasDied = false;
	private Player client;
	
	
	public Player(long id, String name, boolean isHost, int seed, int difficulty, String roomCode) {
		this.id = id;
		this.name = name;
		this.isHost = isHost;
		this.seed = seed;
		this.difficulty = difficulty;
		this.roomCode = roomCode;
	}
	
	public void startGame() { this.hasStarted = true; }
	
	public boolean shouldStart() { return this.hasStarted; }
	
	public void setHost(boolean isHost) { this.isHost = isHost; }
	
	public void setHasDied(boolean hasDied) { this.hasDied = hasDied; }
	
	public void setClientDeath(boolean hasDied) { this.client.setHasDied(hasDied); }
	
	public void setScore(long score) { this.score = score; }

	public void setClientScore(long clientScore) { client.setScore(clientScore); }
	
	public void setTime(long thisTime) { this.time = thisTime; }
	
	public void setClientTime(long clientTime) { client.setTime(clientTime); }
	
	public void setClient(Player client) { this.client = client; }
	
	public Player getClient() { return this.client; }
	
	public boolean getIfHost() { return this.isHost; }
	
	public boolean getIfHasDied() { return this.hasDied; }
	
	public String getName() { return this.name; }
	
	public String getRoomCode() { return this.roomCode; }
	
	public int getSeed() { return this.seed; }
	
	public int getDifficulty() { return this.difficulty; }
	
	public long getScore() { return this.score; }

	public long getClientScore() { return client.getScore(); }
	
	public long getTime() { return this.time; }
	
	public long getClientTime() { return this.client.getTime(); }
	
	public boolean getClientDeath() { return this.client.getIfHasDied(); }
}


