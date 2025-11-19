import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;

/**
 * Console chat client for ChatServer with username/password auth.
 *
 * Protocol:
 *   AUTH <username> <password>
 *   MSG <text>
 *   QUIT
 */
public class ChatClient {

    public static void main(String[] args) {
        Scanner console = new Scanner(System.in);

        // ===== Connection info =====
        System.out.print("Server host (default: localhost): ");
        String host = console.nextLine().trim();
        if (host.isEmpty()) {
            host = "localhost";
        }

        System.out.print("Server port (e.g. 12345): ");
        int port = Integer.parseInt(console.nextLine().trim());

        // ===== Credentials =====
        System.out.print("Username: ");
        String username = console.nextLine().trim();

        System.out.print("Password: ");
        String password = console.nextLine().trim();

        if (username.isEmpty() || password.isEmpty()) {
            System.out.println("Username and password cannot be empty.");
            console.close();
            return;
        }

        // ===== Connect to server =====
        try (Socket socket = new Socket(host, port)) {
            System.out.println("Connected to server " + host + ":" + port);

            BufferedReader in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream()));
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);

            // ===== 1) AUTH handshake =====
            out.println("AUTH " + username + " " + password);

            // Read response (OK or ERROR)
            String response = in.readLine();
            if (response == null) {
                System.out.println("Server closed connection.");
                console.close();
                return;
            }
            System.out.println("Server: " + response);

            if (response.startsWith("ERROR")) {
                console.close();
                return;
            }

            // ===== 2) Listener thread to print incoming messages =====
            Thread listener = new Thread(() -> {
                try {
                    String lineFromServer;
                    while ((lineFromServer = in.readLine()) != null) {
                        System.out.println();
                        System.out.println(lineFromServer);
                        System.out.print("> ");
                    }
                } catch (IOException e) {
                    System.out.println();
                    System.out.println("Connection to server lost.");
                }
                System.out.println("Listener thread exiting.");
            });
            listener.setDaemon(true);
            listener.start();

            // ===== 3) Main loop: send user messages =====
            System.out.println("Type messages and press Enter to send.");
            System.out.println("Type /quit to exit.");
            System.out.print("> ");

            while (true) {
                if (!console.hasNextLine()) {
                    break;
                }
                String input = console.nextLine();

                if (input.equalsIgnoreCase("/quit")) {
                    out.println("QUIT");
                    break;
                }

                if (!input.isBlank()) {
                    out.println("MSG " + input);
                }

                System.out.print("> ");
            }

            System.out.println("Client terminating...");

        } catch (IOException e) {
            System.out.println("Could not connect to server: " + e.getMessage());
        } finally {
            console.close();
        }
    }
}
