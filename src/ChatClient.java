import java.io.IOException;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.net.Socket;

// To recompile files and run client:
// javac src/*.java && java -cp src ChatClient

public class ChatClient {

    public static void main(String[] args) {
        try {
            //readFromFile("LICENSE");
            readFromSocket("localhost", 12345);
        } catch (IOException e) {
            System.err.println("Error reading from socket");
            System.err.println(e.getMessage());
            System.exit(1);
        }
    }

    public static void readFromFile(String filename) throws IOException{
        InputStream inputStream = new FileInputStream(filename);
        InputStreamReader inputStreamReader = new InputStreamReader(inputStream, java.nio.charset.StandardCharsets.UTF_8);
        BufferedReader bufferedReader = new BufferedReader(inputStreamReader);

        String line;
        while((line = bufferedReader.readLine()) != null) {
            System.out.println(line);
        }
    }

    public static void readFromSocket(String host, int port) throws IOException {
        Socket socket = new Socket(host, port);

        InputStream inputStream = socket.getInputStream();
        InputStreamReader inputStreamReader = new InputStreamReader(inputStream, java.nio.charset.StandardCharsets.UTF_8);
        BufferedReader bufferedReader = new BufferedReader(inputStreamReader);

        String line;
        while((line = bufferedReader.readLine()) != null) {
            System.out.println(line);
        }
    }
}
