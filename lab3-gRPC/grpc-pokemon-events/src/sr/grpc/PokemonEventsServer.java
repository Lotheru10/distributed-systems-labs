package sr.grpc;


import io.grpc.BindableService;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import sr.grpc.gen.PokemonEventsGrpc;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

public class PokemonEventsServer {

    private static final Logger logger = Logger.getLogger(PokemonEventsServer.class.getName());

    private String address = "127.0.0.1";
    private int port = 50051;
    private Server server;

    private SocketAddress socket;

    private void start() throws IOException
    {
        try { socket = new InetSocketAddress(InetAddress.getByName(address), port);	} catch(UnknownHostException e) {};

        server = ServerBuilder.forPort(port).executor((Executors.newFixedThreadPool(16)))
                .addService(new PokemonEventsServerImpl())
                .build()
                .start();
        logger.info("Pokemon server started, listening on " + port);
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                System.err.println("Shutting down Pokemon server...");
                PokemonEventsServer.this.stop();
                System.err.println("Server shut down.");
            }
        });
    }

    private void stop() {
        if (server != null) {
            server.shutdown();
        }
    }

    private void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        final PokemonEventsServer server = new PokemonEventsServer();
        server.start();
        server.blockUntilShutdown();
    }

}
