package sr.ice.server;

import com.zeroc.Ice.AlreadyRegisteredException;
import com.zeroc.Ice.Current;
import com.zeroc.Ice.Identity;
import com.zeroc.Ice.ObjectAdapter;
import com.zeroc.Ice.ServantLocator;
import com.zeroc.Ice.UserException;

public class DedicatedPokemonServantLocator implements ServantLocator {
    private final ObjectAdapter adapter;
    private int nextId = 1;

    public DedicatedPokemonServantLocator(ObjectAdapter adapter) {
        this.adapter = adapter;
    }

    @Override
    public synchronized ServantLocator.LocateResult locate(Current current) throws UserException {
        Identity id = current.id;

        System.out.println("Locate " + id.category + "/" + id.name
                + " operation=" + current.operation);

        PokemonI servant = new PokemonI(id.name, id.category, nextId++);

        try {
            adapter.add(servant, id);
        } catch (AlreadyRegisteredException ex) {
            com.zeroc.Ice.Object existingServant = adapter.find(id);
            return new ServantLocator.LocateResult(existingServant, null);
        }

        return new ServantLocator.LocateResult(servant, null);
    }

    @Override
    public void finished(Current current, com.zeroc.Ice.Object servant, java.lang.Object cookie)
            throws UserException {
    }

    @Override
    public void deactivate(String category) {
        System.out.println("deactivate category=" + category);
    }
}