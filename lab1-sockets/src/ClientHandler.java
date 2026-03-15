import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;

public class ClientHandler implements Runnable {

    private final int id;
    private final Socket socket;

    private BufferedReader in;
    private PrintWriter out;

    public ClientHandler(int id, Socket socket) {
        this.id = id;
        this.socket = socket;
    }

    @Override
    public void run() {
        try{
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);
            String message;

            while((message = in.readLine()) != null){
                String wrapMessage = "User" + id + ": "+ message;
                System.out.println(wrapMessage);

                Server.broadcast(this, wrapMessage);
            }
        }
        catch (IOException e){
            throw new RuntimeException(e);
        }

    }
    public void sendMessage(String message){
        out.println(message);
    }
    public int getId() {
        return id;
    }
}
