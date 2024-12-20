import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;
import java.util.*;

public class ChatServer {
  public enum ClientState {
    INIT,
    OUTSIDE,
    INSIDE
  }

  // A pre-allocated buffer for the received data
  static private final ByteBuffer buffer = ByteBuffer.allocate(16384);

  // Decoder for incoming text -- assume UTF-8
  static private final Charset charset = Charset.forName("UTF8");
  static private final CharsetDecoder decoder = charset.newDecoder();

  // Maps to store user names and chat rooms
  static private final Map<SocketChannel, String> userNames = new HashMap<>();
  static private final Map<String, Set<SocketChannel>> chatRooms = new HashMap<>();
  static private final Map<SocketChannel, ClientState> clientStates = new HashMap<>();

  static public void main(String args[]) throws Exception {
    // Parse port from command line
    int port = args.length > 0 ? Integer.parseInt(args[0]) : 8000;

    try {
      // Instead of creating a ServerSocket, create a ServerSocketChannel
      ServerSocketChannel ssc = ServerSocketChannel.open();

      // Set it to non-blocking, so we can use select
      ssc.configureBlocking(false);

      // Get the Socket connected to this channel, and bind it to the
      // listening port
      ServerSocket ss = ssc.socket();
      InetSocketAddress isa = new InetSocketAddress(port);
      ss.bind(isa);

      // Create a new Selector for selecting
      Selector selector = Selector.open();

      // Register the ServerSocketChannel, so we can listen for incoming
      // connections
      ssc.register(selector, SelectionKey.OP_ACCEPT);
      System.out.println("Listening on port " + port);

      while (true) {
        // See if we've had any activity -- either an incoming connection,
        // or incoming data on an existing connection
        int num = selector.select();

        // If we don't have any activity, loop around and wait again
        if (num == 0) {
          continue;
        }

        // Get the keys corresponding to the activity that has been
        // detected, and process them one by one
        Set<SelectionKey> keys = selector.selectedKeys();
        for (SelectionKey key : keys) {
          // Get a key representing one of bits of I/O activity
          // What kind of activity is it?
          if (key.isAcceptable()) {
            // It's an incoming connection. Register this socket with
            // the Selector so we can listen for input on it
            Socket s = ss.accept();
            System.out.println("Got connection from " + s);

            // Make sure to make it non-blocking, so we can use a selector
            // on it.
            SocketChannel sc = s.getChannel();
            sc.configureBlocking(false);

            // Register it with the selector, for reading
            sc.register(selector, SelectionKey.OP_READ);

            // Set initial state
            clientStates.put(sc, ClientState.INIT);
          } else if (key.isReadable()) {

            SocketChannel sc = null;

            try {

              // It's incoming data on a connection -- process it
              sc = (SocketChannel) key.channel();
              boolean ok = processInput(sc);

              // If the connection is dead, remove it from the selector
              // and close it
              if (!ok) {
                key.cancel();

                Socket s = null;
                try {
                  s = sc.socket();
                  System.out.println("Closing connection to " + s);
                  s.close();
                } catch (IOException ie) {
                  System.err.println("Error closing socket " + s + ": " + ie);
                }
              }

            } catch (IOException ie) {

              // On exception, remove this channel from the selector
              key.cancel();

              try {
                sc.close();
              } catch (IOException ie2) {
                System.out.println(ie2);
              }

              System.out.println("Closed " + sc);
            }
          }
        }

        // We remove the selected keys, because we've dealt with them.
        keys.clear();
      }
    } catch (IOException ie) {
      System.err.println(ie);
    }
  }

  // Just read the message from the socket and send it to stdout
  static private boolean processInput(SocketChannel sender)
      throws IOException {

    buffer.clear();

    int bytesRead = sender.read(buffer);
    // If no data, close the connection
    if (bytesRead == -1)
      return false;

    buffer.flip(); // Prepare buffer for reading

    String message = decoder.decode(buffer).toString().trim();
    if (message.startsWith("/") && !message.startsWith("//")) // message is a command
      processCommand(sender, message);
    else if (clientStates.get(sender) == ClientState.INSIDE) {
      broadcastMessage(sender, "MESSAGE " + userNames.get(sender) + " " + message);
      sendMessage(sender, "MESSAGE " + userNames.get(sender) + " " + message);
    } else
      sendMessage(sender, "ERROR [Server] You must join a room using /join before sending messages");

    return true;
  }

  static private void processCommand(SocketChannel sender, String command) throws IOException {
    String[] parts = command.split(" ", 2);
    String cmd = parts[0];
    String arg = parts.length > 1 ? parts[1] : "";

    switch (cmd) {
      // main commands
      case "/nick" -> changeNick(sender, arg);
      case "/join" -> joinRoom(sender, arg);
      case "/leave" -> leaveRoom(sender, true);
      case "/bye" -> disconnect(sender);
      // other commands
      case "/rooms" -> listRooms(sender);
      case "/users" -> listUsers(sender);
      case "/help" -> sendMessage(sender,
          "[Server] Available commands: /nick [username], /join [room], /leave, /bye, /rooms, /users, /help");
      default -> {
        if (clientStates.get(sender) == ClientState.INSIDE) {
          broadcastMessage(sender, "MESSAGE " + userNames.get(sender) + " " + command);
          sendMessage(sender, "MESSAGE " + userNames.get(sender) + " " + command);
        } else
          sendMessage(sender, "ERROR [Server] You must join a room using /join before sending messages");
      }
    }
  }

  static private void changeNick(SocketChannel sender, String newNick) throws IOException {
    if (newNick.isEmpty() || userNames.containsValue(newNick)) {
      sendMessage(sender, "ERROR [Server] Invalid or already taken nickname.");
      return;
    }

    if (clientStates.get(sender) == ClientState.INIT || clientStates.get(sender) == ClientState.OUTSIDE)
      clientStates.put(sender, ClientState.OUTSIDE);
    if (clientStates.get(sender) == ClientState.INSIDE)
      broadcastMessage(sender, "NEWNICK " + userNames.get(sender) + " " + newNick);

    sendMessage(sender, "OK [Server] Nickname changed to " + newNick);
    userNames.put(sender, newNick);
  }

  static private void joinRoom(SocketChannel sender, String room) throws IOException {
    if (clientStates.get(sender) == ClientState.INIT || room.isEmpty()) {
      System.out.println("Client is in INIT state or room is empty");
      return;
    }
    if (clientStates.get(sender) == ClientState.INSIDE) {
      leaveRoom(sender, false);
    }
    sendMessage(sender, "OK [Server] Joined room " + room);
    chatRooms.computeIfAbsent(room, k -> new HashSet<>()).add(sender);
    clientStates.put(sender, ClientState.INSIDE);
    broadcastMessage(sender, "JOINED " + userNames.get(sender));
  }

  static private void leaveRoom(SocketChannel sender, Boolean sendOkToUser) throws IOException {
    if (clientStates.get(sender) != ClientState.INSIDE) {
      System.out.println("Client is in INIT state or is not in a room");
      return;
    }
    for (Set<SocketChannel> room : chatRooms.values()) {
      if (room.contains(sender)) {
        if (sendOkToUser)
          sendMessage(sender, "OK [Server] You left the room");
        broadcastMessage(sender, "LEFT " + userNames.get(sender));
        room.remove(sender);
        clientStates.put(sender, ClientState.OUTSIDE);
        break;
      }
    }
  }

  static private void disconnect(SocketChannel sender) throws IOException {
    if (clientStates.get(sender) == ClientState.INSIDE) {
      leaveRoom(sender, false);
    }
    userNames.remove(sender);
    clientStates.remove(sender);
    sendMessage(sender, "BYE [Server] Disconnected from the server");
    sender.close();
  }

  static private void listRooms(SocketChannel sender) throws IOException {
    if (chatRooms.isEmpty()) {
      sendMessage(sender, "[Server] There are no rooms available, use /join to create one");
      return;
    }
    sendMessage(sender, "[Server] Available rooms:");
    for (String room : chatRooms.keySet()) {
      sendMessage(sender, room);
    }
  }

  static private void listUsers(SocketChannel sender) throws IOException {
    for (Map.Entry<String, Set<SocketChannel>> entry : chatRooms.entrySet()) {
      if (entry.getValue().contains(sender)) {
        sendMessage(sender, "[Server] Users in room " + entry.getKey() + ":");
        for (SocketChannel client : entry.getValue()) {
          sendMessage(sender, userNames.get(client));
        }
        return;
      }
    }
    sendMessage(sender, "[Server] You must join a room using /join before listing users");
  }

  static private void broadcastMessage(SocketChannel sender, String message)
      throws IOException {
    String user = userNames.get(sender);
    if (user == null)
      return;
    for (Set<SocketChannel> room : chatRooms.values()) {
      if (room.contains(sender)) {
        for (SocketChannel client : room)
          if (client != sender)
            sendMessage(client, message);
        return;
      }
    }
  }

  static private void sendMessage(SocketChannel client, String message) throws IOException {
    ByteBuffer buffer = charset.encode(message);
    client.write(buffer);
  }
}