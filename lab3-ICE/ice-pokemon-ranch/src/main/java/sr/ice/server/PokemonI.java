package sr.ice.server;

import PokemonRanch.InvalidAmount;
import PokemonRanch.Pokemon;
import PokemonRanch.PokemonStatus;
import com.zeroc.Ice.Current;

public class PokemonI implements Pokemon {

    private final String name;
    private final String category;
    private final int servantId;

    private int level;
    private int happiness;

    public PokemonI(String name, String category, int servantId) {
        this.name = name;
        this.category = category;
        this.servantId = servantId;
        this.level = 1;
        this.happiness = 50;

        System.out.printf("Create dedicated servant #%d for %s/%s\n", this.servantId, this.category, this.name);
    }

    private PokemonStatus buildStatus() {
        PokemonStatus status = new PokemonStatus();
        status.name = name;
        status.category = category;
        status.level = level;
        status.happiness = happiness;
        status.servantId = servantId;
        return status;
    }

    @Override
    public PokemonStatus train(int amount, Current current) throws InvalidAmount {
        logCall(current.operation, amount);
        if (amount <= 0) {
            throw new InvalidAmount("Training amount must be positive");
        }
        level += amount;
        happiness = Math.min(100, happiness + amount / 2);
        return buildStatus();
    }

    @Override
    public PokemonStatus feed(int amount, Current current) throws InvalidAmount {
        logCall(current.operation, amount);
        if (amount <= 0) {
            throw new InvalidAmount("Feeding amount must be positive");
        }
        happiness = Math.min(100, happiness + amount);
        return buildStatus();
    }

    @Override
    public PokemonStatus rest(Current current) {
        logCall(current.operation, 0);
        happiness = Math.min(100, happiness + 1);
        return buildStatus();
    }

    @Override
    public PokemonStatus getStatus(Current current) {
        logCall(current.operation, 0);
        return buildStatus();
    }

    private void logCall(String operation, int amount) {
        System.out.printf("Call %s/%s %s(%d) on dedicated servant #%d\n", category, name, operation, amount, servantId);
    }
}