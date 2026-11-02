package sr.ice.server;

import com.zeroc.Ice.Communicator;
import com.zeroc.Ice.InitializationException;
import com.zeroc.Ice.ObjectAdapter;
import com.zeroc.Ice.Util;

public class IceServer {

    public void run(String[] args) {
        int status = 0;
        Communicator communicator = null;
        try {
            communicator = Util.initialize(args);
            ObjectAdapter adapter;
            try {
                adapter = communicator.createObjectAdapter("PokemonAdapter");
            } catch (InitializationException ex) {
                System.out.println("(using a hard‑coded configuration)");
                adapter = communicator.createObjectAdapterWithEndpoints(
                        "PokemonAdapter", "tcp -h 127.0.0.1 -p 10000 -z : udp -h 127.0.0.1 -p 10000 -z");
            }

            DedicatedPokemonServantLocator dedicatedLocator = new DedicatedPokemonServantLocator(adapter);
            adapter.addServantLocator(dedicatedLocator, "dedicated");

            SharedPokemonI sharedServant = new SharedPokemonI(100);
            adapter.addDefaultServant(sharedServant, "shared");

            adapter.activate();
            System.out.println("Pokémon Ranch server is running");
            communicator.waitForShutdown();
        } catch (Exception ex) {
            ex.printStackTrace();
            status = 1;
        } finally {
            if (communicator != null) {
                try {
                    communicator.destroy();
                } catch (Exception ex) {
                    ex.printStackTrace();
                    status = 1;
                }
            }
        }
        System.exit(status);
    }

    public static void main(String[] args) {
        new IceServer().run(args);
    }
}