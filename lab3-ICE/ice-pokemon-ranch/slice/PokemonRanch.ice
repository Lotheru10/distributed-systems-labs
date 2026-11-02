module PokemonRanch {
    exception InvalidAmount {
        string reason;
    };

    struct PokemonStatus {
        string name;
        string category;
        int level;
        int happiness;
        int servantId;
    };

    interface Pokemon {
        PokemonStatus train(int amount) throws InvalidAmount;
        PokemonStatus feed(int amount) throws InvalidAmount;
        PokemonStatus rest();
        PokemonStatus getStatus();
    };
};