import queue
import threading

import grpc

from generated import pokemon_events_pb2 as pb2
from generated import pokemon_events_pb2_grpc as pb2_grpc


SERVER_ADDRESS = "localhost:50051"

USER_ID = 1
LAST_RECEIVED = 0
ACTIVE_SUBSCRIPTIONS = []


def request_generator(request_queue: queue.Queue):
    while True:
        request = request_queue.get()

        if request is None:
            break

        yield request


def read_responses(response_iterator, request_queue: queue.Queue):
    global LAST_RECEIVED

    try:
        for response in response_iterator:
            payload = response.WhichOneof("payload")

            if payload == "confirmed":
                print(f"\n[CONFIRMED] subscription_id={response.confirmed.subscription_id}")

            elif payload == "notification":
                notification = response.notification
                event = notification.event

                LAST_RECEIVED = notification.notification_id

                types = ", ".join(
                    pb2.PokemonType.Name(pokemon_type)
                    for pokemon_type in event.types
                )

                print(
                    f"\n[NOTIFICATION #{notification.notification_id}] "
                    f"sub={notification.subscription_id}, "
                    f"{pb2.EventType.Name(event.event_type)} in {event.city}: "
                    f"{event.pokemon_name}, "
                    f"level={event.level}, "
                    f"types=[{types}]"
                )

                ack = pb2.PokemonRequest(
                    user_id=USER_ID,
                    ack=pb2.Ack(notification_id=notification.notification_id),
                )

                request_queue.put(ack)

            elif payload == "error":
                print(
                    f"\n[ERROR] "
                    f"{pb2.ErrorCode.Name(response.error.code)}: "
                    f"{response.error.message}"
                )

    except grpc.RpcError as error:
        print(f"\n[STREAM ERROR] {error.code()}: {error.details()}")


def connect(user_id: int, reconnect: bool = False):
    request_queue = queue.Queue()

    channel = grpc.insecure_channel(SERVER_ADDRESS)
    stub = pb2_grpc.PokemonEventsStub(channel)

    if reconnect:
        request_queue.put(
            pb2.PokemonRequest(
                user_id=user_id,
                reconnect=pb2.Reconnect(last_received_notification=0),
            )
        )

    responses = stub.Event(request_generator(request_queue))

    reader_thread = threading.Thread(
        target=read_responses,
        args=(responses, request_queue),
        daemon=True,
    )
    reader_thread.start()

    return request_queue, channel


def subscribe(request_queue: queue.Queue, user_id: int):
    city = input("city: ").strip()
    event_name = input("event [RAID/SPAWN/COMMUNITY_DAY/EGG]: ").strip().upper()
    types_raw = input("types comma-separated [e.g. FIRE,WATER]: ").strip().upper()
    min_level = int(input("min_level: ").strip())

    pokemon_types = []

    if types_raw:
        for pokemon_type in types_raw.split(","):
            pokemon_types.append(pb2.PokemonType.Value(pokemon_type.strip()))

    request = pb2.PokemonRequest(
        user_id=user_id,
        subscribe=pb2.Subscribe(
            event=pb2.EventType.Value(event_name),
            city=city,
            types=pokemon_types,
            min_level=min_level,
        ),
    )

    request_queue.put(request)


def unsubscribe(request_queue: queue.Queue, user_id: int):
    subscription_id = int(input("subscription_id: ").strip())
    reason = input("reason: ").strip()

    request = pb2.PokemonRequest(
        user_id=user_id,
        unsubscribe=pb2.Unsubscribe(
            subscription_id=subscription_id,
            reason=reason,
        ),
    )

    request_queue.put(request)

def read_int(prompt: str) -> int:
    while True:
        value = input(prompt).strip()
        try:
            return int(value)
        except ValueError:
            print("Please enter a valid integer.")

def main():
    global USER_ID, LAST_RECEIVED

    USER_ID = read_int("user_id: ")

    request_queue, channel = connect(USER_ID, reconnect=True)

    print("Commands: subscribe | unsubscribe | disconnect | reconnect | exit")

    while True:
        cmd = input("> ").strip().lower()

        if cmd == "subscribe":
            subscribe(request_queue, USER_ID)

        elif cmd == "unsubscribe":
            unsubscribe(request_queue, USER_ID)

        elif cmd == "reconnect":
            print(f"[RECONNECT] last_received={LAST_RECEIVED}")
            request_queue, channel = connect(USER_ID, LAST_RECEIVED)

        elif cmd == "exit":
            request_queue.put(None)
            channel.close()
            break

        else:
            print("Unknown command")


if __name__ == "__main__":
    main()