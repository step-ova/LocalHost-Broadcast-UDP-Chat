import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.time.LocalDateTime;
import java.util.ArrayList;

public class MainClass {
	
	public static final String JOIN_COMMAND = "join";
	public static final String TALK_COMMAND = "talk";
	public static final String PING_COMMAND = "ping";
	public static final String QUIT_COMMAND = "quit";
	
	public static final String WHO_MESSAGE = "/who";
	public static final String LEAVE_MESSAGE = "/leave";

	public static void main(String[] args) throws Exception {
		String hostname = "255.255.255.255";
		InetAddress ia = InetAddress.getByName(hostname);
		
		System.out.print("Enter your name: ");
		BufferedReader userInput = new BufferedReader(new InputStreamReader(System.in));
		String username = userInput.readLine();
		int port = 8888;

		SenderThread sender = new SenderThread(username, ia, port);
		sender.start();
		Thread receiver = new ReceiverThread(sender.getSocket());
		receiver.start();
	}

}

class SenderThread extends Thread {

	private InetAddress server;

	private DatagramSocket socket;

	private String username;

	private int port;

	public SenderThread(String username, InetAddress address, int port) throws SocketException {
		this.username = username;
		this.server = address;
		this.port = port;
		this.socket = new DatagramSocket(port);
		this.socket.setReuseAddress(true);
	}

	public DatagramSocket getSocket() {
		return this.socket;
	}

	public void run() {

		try {

			BufferedReader userInput = new BufferedReader(new InputStreamReader(System.in));
			String join = username + " joined!";
			String joinMessage = CommonCode.buildMessage(join, username, MainClass.JOIN_COMMAND);
			byte[] joinData = joinMessage.getBytes();
			DatagramPacket joinOutput = new DatagramPacket(joinData, joinData.length, server, port);
			socket.send(joinOutput);
			

			while (true) {
				String message = userInput.readLine();
				String builtMessaage = CommonCode.buildMessage(message, username, MainClass.TALK_COMMAND);
				byte[] data = builtMessaage.getBytes();

				if (message.trim().equalsIgnoreCase(MainClass.WHO_MESSAGE)) {
					server = InetAddress.getByName("localhost");
				}

				DatagramPacket output = new DatagramPacket(data, data.length, server, port);
				socket.send(output);

				//If after sending the socket the message was to leave then send quit locally
				if (message.trim().equalsIgnoreCase(MainClass.LEAVE_MESSAGE)) {
					String quitMessage = CommonCode.buildMessage("", username, MainClass.QUIT_COMMAND);
					byte[] quitData = quitMessage.getBytes();
					InetAddress server = InetAddress.getByName("localhost");
					DatagramPacket outputQuit = new DatagramPacket(quitData, quitData.length, server, port);
					socket.send(outputQuit);
				}
				
				//reset server to broadcast
				server = InetAddress.getByName("255.255.255.255");
				
				Thread.yield();
			}
		} catch (IOException ex) {
			System.err.println(ex);
		}
	}

}

class ReceiverThread extends Thread {
	
	private ArrayList<String> clientList;
	private DatagramSocket socket;

	public ReceiverThread(DatagramSocket ds) throws SocketException {
		this.socket = ds;
		clientList = new ArrayList<>();
	}

	public void run() {
		byte[] buffer = new byte[65507];
		while (true) {
			DatagramPacket dp = new DatagramPacket(buffer, buffer.length);
			try {
				socket.receive(dp);
				String s = new String(dp.getData(), 0, dp.getLength());
				String receivedMessage = getMessage(s);
				String receivedUsername = getUsername(s);
				String receivedCommand = getCommand(s);

				// only print if command is talk or join
				if (receivedCommand.equalsIgnoreCase(MainClass.JOIN_COMMAND) || receivedCommand.equalsIgnoreCase(MainClass.TALK_COMMAND)) {

					if (receivedCommand.equalsIgnoreCase(MainClass.JOIN_COMMAND)) {
						clientList.add(receivedUsername);
						
						String pingMessage = CommonCode.buildMessage("", clientList.get(0), MainClass.PING_COMMAND);
						byte[] pingData = pingMessage.getBytes();
						DatagramPacket pingOutput = new DatagramPacket(pingData, pingData.length, InetAddress.getByName("255.255.255.255"), socket.getLocalPort());
						socket.send(pingOutput);
					}

					if (receivedMessage.equalsIgnoreCase(MainClass.LEAVE_MESSAGE)) {
						clientList.remove(receivedUsername);
						printLeaveMessage(receivedUsername);
					}

					else if (receivedMessage.equalsIgnoreCase(MainClass.WHO_MESSAGE)) {
						printUsers();
					}
					
					//Talk
					else {
						printServerMessage(receivedMessage, receivedUsername);
					}

				}
				else if(receivedCommand.equalsIgnoreCase(MainClass.QUIT_COMMAND)){
					System.out.println("Bye now!");
					socket.close();
					System.exit(0);
				}
				else if(receivedCommand.equalsIgnoreCase(MainClass.PING_COMMAND)){
					if(!clientList.contains(receivedUsername)){
						clientList.add(receivedUsername);
					}
						
				}

				Thread.yield();
			} catch (IOException ex) {
				System.err.println(ex);
			}
		}
	}

	private String getUsername(String senderMessage) {
		String[] messageSplit = senderMessage.split("\n");
		return messageSplit[0].substring(6, messageSplit[0].length()).trim();
	}

	private String getMessage(String senderMessage) {
		String[] messageSplit = senderMessage.split("\n");
		return messageSplit[2].substring(9, messageSplit[2].length()).trim();
	}

	private String getCommand(String senderMessage) {
		String[] messageSplit = senderMessage.split("\n");
		return messageSplit[1].substring(9, messageSplit[1].length()).trim();
	}

	private void printServerMessage(String message, String username) {
		StringBuilder sb = new StringBuilder();
		LocalDateTime timePoint = LocalDateTime.now();

		sb.append(timePoint.toString());
		sb.append(" [");
		sb.append(username);
		sb.append("]: ");
		sb.append(message);

		System.out.println(sb.toString());

	}

	private void printLeaveMessage(String username) {
		StringBuilder sb = new StringBuilder();
		LocalDateTime timePoint = LocalDateTime.now();

		sb.append(timePoint.toString());
		sb.append(" ");
		sb.append(username);
		sb.append(" left!");

		System.out.println(sb.toString());

	}

	private void printUsers() {
		StringBuilder sb = new StringBuilder();
		LocalDateTime timePoint = LocalDateTime.now();

		sb.append(timePoint.toString());
		sb.append(" Connected users: [");
		for (String user : clientList) {
			sb.append(user);
			sb.append(", ");
		}
		sb.setLength(sb.length() - 2);
		System.out.println(sb.toString() + "]");
	}


}

class CommonCode{
	public static String buildMessage(String message, String username, String command) {
		StringBuilder sb = new StringBuilder();
		sb.append("user: ");
		sb.append(username);
		sb.append('\n');

		sb.append("command: ");
		sb.append(command);
		sb.append('\n');

		sb.append("message: ");
		sb.append(message);
		sb.append('\n');

		sb.append('\n');

		return sb.toString();
	}
}