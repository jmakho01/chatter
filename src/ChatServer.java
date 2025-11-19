import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Simple multi-user chat server with CSV-based username/password auth.
 *
 * users.csv format (one per line, no spaces):
 *   username,password
 *   alice,secret123
 *   bob,password
 *
 * Protocol (per connection):
 *   1) Client connects.
 *   2) Client sends: AUTH <username> <password>
 *   3) Server replies: OK Welcome, <username>!  OR ERROR ...
 *   4) Messages:      MSG <text>
 *   5) Quit:          QUIT
 *
 * Messages are broadcast as "username: text" to all other clients.
 */
public class ChatServer {

    // === CONFIGURATION ===

    // Port to listen on
    private static final int PORT = 12345;

    // Maximum number of clients connected at once
    private static final int MAX_CLIENTS = 50;

    // Maximum characters per line (protocol line)
    private static final int MAX_LINE_LENGTH = 512;

    // Path to CSV with username,password pairs
    private final String usersCsvPath;

    // =======================

    private final Set<ClientHandler> clients =
            Collections.synchronizedSet(new HashSet<>());

    private final Map<String, String> userPasswordMap = new HashMap<>();

    public ChatServer(String usersCsvPath) {
        this.usersCsvPath = usersCsvPath;
    }

    public static void main(String[] args) {
        // Require CSV path
        if (args.length != 1) {
            System.out.println("Usage: java ChatServer <users.csv>");
            System.exit(1);
        }

        String csvPath = args[0];

        ChatServer server = new ChatServer(csvPath);
        try {
            server.loadUsersFromCsv(csvPath);
            server.start();
        } catch (IOException e) {
            System.out.println("Fatal server error: " + e.getMessage());
        }
    }

    private void loadUsersFromCsv(String path) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(path))) {
            String line;
            int count = 0;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String[] parts = line.split(",", 2);
                if (parts.length != 2) {
                    System.out.println("Skipping malformed CSV line: " + line);
                    continue;
                }
                String username = parts[0].trim();
                String password = parts[1].trim();
                if (!username.isEmpty()) {
                    userPasswordMap.put(username, password);
                    count++;
                }
            }
            System.out.println("Loaded " + count + " user(s) from " + path);
        }
    }

    private void start() throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Chat server listening on port " + PORT);
            System.out.println("Max clients: " + MAX_CLIENTS);

            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("Incoming connection from " + socket.getRemoteSocketAddress());
                ClientHandler handler = new ClientHandler(socket);
                Thread t = new Thread(handler);
                t.start();
            }
        }
    }

    // Broadcast a message to all clients except the sender (if non-null)
    private void broadcast(String message, ClientHandler from) {
        synchronized (clients) {
            for (ClientHandler client : clients) {
                if (client != from) {
                    client.send(message);
                }
            }
        }
    }

    private void removeClient(ClientHandler handler) {
        clients.remove(handler);
    }

    // ====================================
    // Per-client handler
    // ====================================
    private class ClientHandler implements Runnable {
        private final Socket socket;
        private BufferedReader in;
        private PrintWriter out;
        private String username;

        ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                // ===== 1) AUTH username password =====
                String authLine = in.readLine();
                if (authLine == null || !authLine.startsWith("AUTH ")) {
                    send("ERROR Expected AUTH <username> <password>.");
                    return;
                }

                if (authLine.length() > MAX_LINE_LENGTH) {
                    send("ERROR Line too long.");
                    return;
                }

                String[] parts = authLine.split(" ", 3);
                if (parts.length != 3) {
                    send("ERROR Expected AUTH <username> <password>.");
                    return;
                }

                String providedUsername = parts[1].trim();
                String providedPassword = parts[2].trim();

                if (providedUsername.isEmpty() || providedPassword.isEmpty()) {
                    send("ERROR Username and password cannot be empty.");
                    return;
                }

                // Check credentials against CSV
                String expectedPassword = userPasswordMap.get(providedUsername);
                if (expectedPassword == null || !expectedPassword.equals(providedPassword)) {
                    send("ERROR Invalid username or password.");
                    return;
                }

                username = providedUsername;

                // Register client if capacity allows
                synchronized (clients) {
                    if (clients.size() >= MAX_CLIENTS) {
                        send("ERROR Server is full. Try again later.");
                        return;
                    }
                    clients.add(this);
                }

                send("OK Welcome, " + username + "!");
                broadcast("[Server] " + username + " joined the chat.", this);
                System.out.println(username + " joined from " + socket.getRemoteSocketAddress());

                // ===== 2) Main message loop =====
                String line;
                while ((line = in.readLine()) != null) {
                    if (line.length() > MAX_LINE_LENGTH) {
                        send("ERROR Line too long (>" + MAX_LINE_LENGTH + " chars).");
                        continue;
                    }

                    if (line.startsWith("MSG ")) {
                        String msg = line.substring("MSG ".length()).trim();
                        if (!msg.isEmpty()) {
                            String full = username + ": " + msg;
                            broadcast(full, this);
                            // Echo to sender
                            send("(you) " + msg);
                        }
                    } else if (line.equals("QUIT")) {
                        send("OK Goodbye.");
                        break;
                    } else {
                        send("ERROR Unknown command.");
                    }
                }

            } catch (IOException e) {
                System.out.println("Connection error with "
                        + usernameOrAddress() + ": " + e.getMessage());
            } finally {
                try { socket.close(); } catch (IOException ignored) {}

                removeClient(this);
                if (username != null) {
                    broadcast("[Server] " + username + " left the chat.", this);
                    System.out.println(username + " disconnected.");
                } else {
                    System.out.println("Client " + socket.getRemoteSocketAddress()
                            + " disconnected before successful AUTH.");
                }
            }
        }

        void send(String message) {
            if (out != null) {
                out.println(message);
            }
        }

        private String usernameOrAddress() {
            return (username != null ? username : String.valueOf(socket.getRemoteSocketAddress()));
        }
    }
}
