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
		if (args.length != 1) {
            System.err.println("Usage: java Server <port number>");
            System.exit(1);
        }
		
		int portNumber = Integer.parseInt(args[0]);
		
		ArrayList<Player> players = new ArrayList<Player>();
		Hashtable<String, Player> lobbyTable = new Hashtable<String, Player>();
        ServerSocket serverSocket = new ServerSocket(portNumber);
        
        while(true) {
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
	        
	        JSONObject jsonObject = new JSONObject(received);
	        //System.out.println(jsonObject);
	        
	        String JSONReply = new JSONObject().put("response", "bad").toString();
					
	        long id;
	        String roomCode;
	        int seed = 0;
	        boolean doesRoomExist = false;
	        switch( jsonObject.getString("action") ) {
	        case "NewLobby":
	        	// Create new player with room code
	        	id = new Random().nextLong() & Long.MAX_VALUE;
	        	// Make sure random room code does not already exist
	        	roomCode = String.valueOf(new Random().nextInt(9999));
	        	while(lobbyTable.get(roomCode) != null) {
	        		roomCode = String.valueOf(new Random().nextInt(9999));
	        	}
	        	
	        	Player newPlayer = new Player(
	        			id, 
						jsonObject.getString("name"), 
						true, 
						jsonObject.getInt("seed"),
						roomCode);
	        	
	        	players.add(newPlayer);
	        	lobbyTable.put(roomCode, newPlayer);
	        	
	        	JSONReply = new JSONObject()
	    						.put("response", "OK")
	    						.put("id", id)
	    						.put("roomCode", roomCode)
	    						.toString();
	        	break;
	        case "JoinRoom":
	        	// Connect a player to a room
	        	id = new Random().nextLong() & Long.MAX_VALUE;
	        	roomCode = jsonObject.getString("roomCode");
	        	
	        	doesRoomExist = false;
        		if (lobbyTable.get(roomCode) != null) {
        			doesRoomExist = true;
        			seed = lobbyTable.get(roomCode).getSeed();
        		}
	        	
	        	if (doesRoomExist) {
	        		Player host = lobbyTable.get(roomCode);
	        		host.startGame();
	        		host.setClient(new Player(
		        			id, 
							jsonObject.getString("name"), 
							false, 
							seed,
							roomCode
							));
	        		
		        	players.add(host.getClient());
		        	
		        	JSONReply = new JSONObject()
							.put("response", "OK")
							.put("id", id)
							.put("seed", seed)
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
								.put("enemyName", host.getName())
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
	        	
	        	
	        	Player getHost = lobbyTable.get(roomCode);
	        	if (getHost != null) {
	        		// Determine if the submitter is host and apply their time/score to
	        		// the host's object.
		        	if (isHost) {
		        		getHost.setTime(time);
		        		getHost.setScore(score);
		        		
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
									.toString();
		        		}
		        	// If not Host
		        	}else {
		        		getHost.setClientTime(time);
		        		getHost.setClientScore(score);
		        		
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

class Player {
	private long id;
	private String name;
	boolean isHost = false;
	private int seed;
	private String roomCode;
	private long score;
	private long time = 0;
	private boolean hasStarted = false;
	private Player client;
	
	//private long clientScore;
	//private long clientTime = 0;
	
	
	public Player(long id, String name, boolean isHost, int seed, String roomCode) {
		this.id = id;
		this.name = name;
		this.isHost = isHost;
		this.seed = seed;
		this.roomCode = roomCode;
	}
	
	public void startGame() {
		this.hasStarted = true;
	}
	
	public boolean shouldStart() {
		return hasStarted;
	}
	
	public String getName() {
		return this.name;
	}
	
	public String getRoomCode() {
		return roomCode;
	}
	
	public int getSeed() {
		return seed;
	}
	
	public long getScore() {
		return score;
	}

	public long getClientScore() {
		return client.getScore();
	}
	
	public long getTime() {
		return time;
	}
	
	public long getClientTime() {
		return client.getTime();
	}
	
	public void setScore(long score) {
		this.score = score;
	}

	public void setClientScore(long clientScore) {
		client.setScore(clientScore);
	}
	
	public void setTime(long thisTime) {
		this.time = thisTime;
	}
	
	public void setClientTime(long clientTime) {
		client.setTime(clientTime);;
	}
	
	public void setClient(Player client) {
		this.client = client;
	}
	
	public Player getClient() {
		return this.client;
	}
}


