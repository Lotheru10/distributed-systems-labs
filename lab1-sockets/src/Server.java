import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class Server {
    private static ServerSocket serverSocket = null;
    private static DatagramSocket udpServerSocket = null;
    private static List<ClientHandler> clients = new ArrayList<>();

    public static void main(String[] args){
        try{
            serverSocket = new ServerSocket(12345);
            udpServerSocket = new DatagramSocket(12345);

            Thread udpListener = getUdpThread(udpServerSocket);

            udpListener.start();
            while (true){
                Socket clientSocket = serverSocket.accept();
                int currentId = clientSocket.getPort();
                ClientHandler clientHandler = new ClientHandler(currentId, clientSocket);
                clients.add(clientHandler);

                new Thread(clientHandler).start();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    public static void broadcast(ClientHandler sender, String message){
        for (ClientHandler client : clients ){
            if (client != sender){
                client.sendMessage(message);
            }
        }
    }

    public static Thread getUdpThread(DatagramSocket udpServerSocket){
        byte[] receiveBuffer = new byte[1024];
        Thread thread = new Thread (() -> {
            try {
                while(true) {
                    DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
                    udpServerSocket.receive(receivePacket);
                    String wrappedMsg = new String(receivePacket.getData(), 0, receivePacket.getLength());
                    int spaceIndex = wrappedMsg.indexOf(" ");
                    int id = Integer.parseInt(wrappedMsg.substring(0, spaceIndex));
                    String message = wrappedMsg.substring(spaceIndex+1);
                    ClientHandler sender = findClientById(id);
                    String readyMessage = "[UDP] "+"User"+id+": "+message;
                    System.out.println(readyMessage);
                    broadcast(sender, readyMessage);
                }
            }
            catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        return thread;
    }

    private static ClientHandler findClientById(int id){
    for (ClientHandler client : clients){
        if(client.getId()==id){
            return client;
        }
    }
    return null;
    }
}
