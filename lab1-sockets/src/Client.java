import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class Client {
    public static void main(String[] args) {
        Socket socket = null;
        DatagramSocket udpSocket = null;

        try {
            socket = new Socket("localhost", 12345);
            int clientId = socket.getLocalPort();

            udpSocket = new DatagramSocket();
            InetAddress address = InetAddress.getByName("localhost");

            InetAddress group = InetAddress.getByName("228.5.6.7");
            MulticastSocket multiSocket = new MulticastSocket(6789);
            multiSocket.joinGroup(group);


            Thread reader = getReaderThread(socket);
            reader.start();

            Thread multiReader = getMultiReaderThread(multiSocket, clientId);
            multiReader.start();

            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader keyboard = new BufferedReader(new InputStreamReader(System.in));

            String input;
            while ((input = keyboard.readLine()) != null) {
                if (input.equals("U")){
                    input = clientId+ " " +keyboard.readLine();
                    DatagramPacket sendPacket = new DatagramPacket(input.getBytes(), input.length(), address, 12345);
                    udpSocket.send(sendPacket);
                } else if (input.equals("M")) {
                    input = "User"+clientId+ ": " +keyboard.readLine();
                    DatagramPacket multi = new DatagramPacket(input.getBytes(), input.length(), group, 6789);
                    multiSocket.send(multi);
                } else {
                    out.println(input);
                }
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            if (socket != null && udpSocket!= null) {
                try {
                    socket.close();
                    udpSocket.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    private static Thread getReaderThread(Socket socket) throws IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        Thread thread = new Thread(() -> {
            try {
                String msg;
                while ((msg = in.readLine()) != null) {
                    System.out.println(msg);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        return thread;
    }

    private static Thread getMultiReaderThread(MulticastSocket socket, int clientId) throws IOException{
        byte[] buf = new byte[1024];
        Thread thread = new Thread(() -> {
          try{
              while (true) {
                  DatagramPacket recv = new DatagramPacket(buf, buf.length);
                  socket.receive(recv);
                  String msg = new String(recv.getData(), 0, recv.getLength());
                  int id = extractId(msg);
                  if (id != clientId ){
                      System.out.println("[Multi] "+msg);
                  }
              }
          }
          catch (IOException e) {
              throw new RuntimeException(e);
          }
        });
        return thread;
    }
    private static int extractId(String message){
        int endOfId = message.indexOf(":");
        return Integer.parseInt(message.substring(4, endOfId));
    }


    }