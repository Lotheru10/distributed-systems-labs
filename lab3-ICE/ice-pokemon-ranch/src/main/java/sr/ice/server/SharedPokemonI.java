package sr.ice.server;

import PokemonRanch.InvalidAmount;
import PokemonRanch.Pokemon;
import PokemonRanch.PokemonStatus;
import com.zeroc.Ice.Current;

import java.util.HashMap;
import java.util.Map;


public class SharedPokemonI implements Pokemon {

    private static class State {
        int level = 1;
        int happiness = 50;
    }

    private final Map<String, State> states = new HashMap<>();
    private final int servantId;

    public SharedPokemonI(int servantId) {
        this.servantId = servantId;
        System.out.printf("Created shared servant #%d\n", servantId);
    }

    private State getState(String name) {
        return states.computeIfAbsent(name, k -> new State());
    }

    private PokemonStatus buildStatus(String name, String category, State state) {
        PokemonStatus status = new PokemonStatus();
        status.name = name;
        status.category = category;
        status.level = state.level;
        status.happiness = state.happiness;
        status.servantId = servantId;
        return status;
    }

    @Override
    public PokemonStatus train(int amount, Current current) throws InvalidAmount {
        String name = current.id.name;
        String category = current.id.category;
        logCall(name, category, current.operation, amount);
        if (amount <= 0) {
            throw new InvalidAmount("Training amount must be positive");
        }
        State state = getState(name);
        state.level += amount;
        state.happiness = Math.min(100, state.happiness + amount / 2);
        return buildStatus(name, category, state);
    }

    @Override
    public PokemonStatus feed(int amount, Current current) throws InvalidAmount {
        String name = current.id.name;
        String category = current.id.category;
        logCall(name, category, current.operation, amount);
        if (amount <= 0) {
            throw new InvalidAmount("Feeding amount must be positive");
        }
        State state = getState(name);
        state.happiness = Math.min(100, state.happiness + amount);
        return buildStatus(name, category, state);
    }

    @Override
    public PokemonStatus rest(Current current) {
        String name = current.id.name;
        String category = current.id.category;
        logCall(name, category, current.operation, 0);
        State state = getState(name);
        state.happiness = Math.min(100, state.happiness + 1);
        return buildStatus(name, category, state);
    }

    @Override
    public PokemonStatus getStatus(Current current) {
        String name = current.id.name;
        String category = current.id.category;
        logCall(name, category, current.operation, 0);
        State state = getState(name);
        return buildStatus(name, category, state);
    }

    private void logCall(String name, String category, String operation, int amount) {
        System.out.printf("Call %s/%s %s(%d) on shared servant #%d\n", category, name, operation, amount, servantId);
    }
}