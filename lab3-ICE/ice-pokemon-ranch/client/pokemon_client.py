#!/usr/bin/env python3

import sys
import shlex


import Ice

try:
    import PokemonRanch
except ImportError:
    print("Error: Could not import PokemonRanch module. Have you run slice2py on PokemonRanch.ice?")
    sys.exit(1)

PORT = 10000
def parse_use_args(tokens):
    if len(tokens) != 3:
        raise ValueError("Usage: use <category> <name>")
    return tokens[1], tokens[2]


def build_proxy_string(category, name, port):
    return f"{category}/{name}:tcp -h 127.0.0.1 -p {port} -z"


def main():
    with Ice.initialize(sys.argv) as communicator:
        current_pokemon = None
        current_identity = None

        print("Pokémon Ranch Python Client")
        print("Type 'help' for a list of commands.")
        while True:
            try:
                line = input("ranch> ").strip()
            except EOFError:
                break
            if not line:
                continue
            tokens = shlex.split(line)
            cmd = tokens[0].lower()

            try:
                if cmd in {"quit", "exit"}:
                    break
                elif cmd == "use":
                    category, name = parse_use_args(tokens)

                    proxy_str = build_proxy_string(category, name, PORT)
                    base = communicator.stringToProxy(proxy_str)
                    pokemon = PokemonRanch.PokemonPrx.checkedCast(base)
                    if not pokemon:
                        print("Invalid proxy or object does not implement Pokemon")
                        continue
                    current_pokemon = pokemon
                    current_identity = (category, name)
                    print(f"Selected Pokémon {category}/{name}")
                elif cmd in {"checked", "unchecked"}:
                    if len(tokens) != 3:
                        print("Usage: checked <category> <name>")
                        continue
                    category, name = tokens[1], tokens[2]
                    proxy_str = build_proxy_string(category, name, PORT)
                    base = communicator.stringToProxy(proxy_str)
                    if cmd == "checked":
                        _ = PokemonRanch.PokemonPrx.checkedCast(base)
                        print("checkedCast completed")
                    else:
                        _ = PokemonRanch.PokemonPrx.uncheckedCast(base)
                        print("uncheckedCast completed")
                elif cmd == "status":
                    if not current_pokemon:
                        print("No Pokémon selected. Use the 'use' command first.")
                        continue
                    status = current_pokemon.getStatus()
                    print(f"Name: {status.name}, Category: {status.category}, "
                          f"Level: {status.level}, Happiness: {status.happiness}, "
                          f"ServantId: {status.servantId}")
                elif cmd == "train":
                    if not current_pokemon:
                        print("No Pokémon selected. Use the 'use' command first.")
                        continue
                    if len(tokens) != 2:
                        print("Usage: train <amount>")
                        continue
                    amount = int(tokens[1])
                    status = current_pokemon.train(amount)
                    print(f"After training: Level {status.level}, Happiness {status.happiness}")
                elif cmd == "feed":
                    if not current_pokemon:
                        print("No Pokémon selected. Use the 'use' command first.")
                        continue
                    if len(tokens) != 2:
                        print("Usage: feed <amount>")
                        continue
                    amount = int(tokens[1])
                    status = current_pokemon.feed(amount)
                    print(f"After feeding: Level {status.level}, Happiness {status.happiness}")
                elif cmd == "rest":
                    if not current_pokemon:
                        print("No Pokémon selected. Use the 'use' command first.")
                        continue
                    status = current_pokemon.rest()
                    print(f"After resting: Level {status.level}, Happiness {status.happiness}")
                else:
                    print("Unknown command. Type 'help' for a list of commands.")
            except PokemonRanch.InvalidAmount as ex:
                print(f"Error: {ex.reason}")
            except Ice.Exception as ex:
                print(f"Ice error: {ex}")
            except ValueError as ex:
                print(f"Error: {ex}")
        print("Goodbye!")


if __name__ == '__main__':
    main()