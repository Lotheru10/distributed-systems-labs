package sr.grpc;

import io.grpc.stub.StreamObserver;
import sr.grpc.gen.*;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public class PokemonEventsServerImpl extends PokemonEventsGrpc.PokemonEventsImplBase {
    private final AtomicInteger nextSubscriptionId = new AtomicInteger(1);
    private final AtomicInteger nextNotificationId = new AtomicInteger(1);
    private final AtomicInteger nextEventId = new AtomicInteger(1);

    private final Map<Integer, UserSubscription> subscriptions = new ConcurrentHashMap<>();
    private final Map<Integer, StreamObserver<PokemonResponse>> activeClients = new ConcurrentHashMap<>();
    private final Map<Integer, List<Notification>> pendingNotifications = new ConcurrentHashMap<>();

    private volatile boolean generatorStarted = false;

    private record UserSubscription(int userId, Subscribe subscribe) {
    }

    @Override
    public StreamObserver<PokemonRequest> event(StreamObserver<PokemonResponse> responseObserver) {
        startEventGeneratorIfNeeded();

        System.out.println("New client connected");

        return new StreamObserver<>() {
            private int userId = -1;

            @Override
            public void onNext(PokemonRequest request) {
                userId = request.getUserId();

                activeClients.put(userId, responseObserver);
                pendingNotifications.computeIfAbsent(userId, id -> new CopyOnWriteArrayList<>());

                switch (request.getPayloadCase()) {
                    case SUBSCRIBE -> handleSubscribe(request.getSubscribe());
                    case UNSUBSCRIBE -> handleUnsubscribe(request.getUnsubscribe());
                    case ACK -> handleAck(request.getAck());
                    case RECONNECT -> handleReconnect();
                    case PAYLOAD_NOT_SET -> sendError(responseObserver, "Empty request payload");
                }
            }

            @Override
            public void onError(Throwable throwable) {
                activeClients.remove(userId);

                System.out.println(
                        "User " + userId + " disconnected with error: " + throwable.getMessage()
                );
            }

            @Override
            public void onCompleted() {
                activeClients.remove(userId);

                System.out.println("User " + userId + " completed stream");
                responseObserver.onCompleted();
            }

            private void handleSubscribe(Subscribe subscribe) {
                int subscriptionId = nextSubscriptionId.getAndIncrement();

                subscriptions.put(subscriptionId, new UserSubscription(userId, subscribe));

                SubscribeConfirm confirm = SubscribeConfirm.newBuilder()
                        .setSubscriptionId(subscriptionId)
                        .build();

                responseObserver.onNext(
                        PokemonResponse.newBuilder()
                                .setConfirmed(confirm)
                                .build()
                );

                System.out.println(
                        "User " + userId
                                + " subscribed with subscription_id=" + subscriptionId
                                + ", city=" + subscribe.getCity()
                                + ", event=" + subscribe.getEvent()
                                + ", min_level=" + subscribe.getMinLevel()
                );
            }

            private void handleUnsubscribe(Unsubscribe unsubscribe) {
                int subscriptionId = unsubscribe.getSubscriptionId();
                UserSubscription removed = subscriptions.remove(subscriptionId);

                if (removed != null) {
                    System.out.println(
                            "User " + userId
                                    + " unsubscribed from subscription_id=" + subscriptionId
                                    + ", reason=" + unsubscribe.getReason()
                    );
                } else {
                    sendError(responseObserver, "Subscription not found: " + subscriptionId);
                }
            }

            private void handleAck(Ack ack) {
                List<Notification> buffer = pendingNotifications.get(userId);

                if (buffer == null) {
                    return;
                }

                boolean removed = buffer.removeIf(
                        notification -> notification.getNotificationId() == ack.getNotificationId()
                );

                if (removed) {
                    System.out.println(
                            "ACK from user=" + userId
                                    + ", removed notification_id=" + ack.getNotificationId()
                    );
                } else {
                    System.out.println(
                            "ACK from user=" + userId
                                    + ", notification not found=" + ack.getNotificationId()
                    );
                }
            }

            private void handleReconnect() {
                activeClients.put(userId, responseObserver);

                List<Notification> buffer = pendingNotifications.get(userId);

                if (buffer == null || buffer.isEmpty()) {
                    System.out.println("Reconnect user=" + userId + ", no pending notifications");
                    return;
                }

                System.out.println(
                        "Reconnect user=" + userId
                                + ", resending pending notifications=" + buffer.size()
                );

                for (Notification notification : buffer) {
                    responseObserver.onNext(
                            PokemonResponse.newBuilder()
                                    .setNotification(notification)
                                    .build()
                    );

                    System.out.println(
                            "Resent notification_id="
                                    + notification.getNotificationId()
                                    + " to user=" + userId
                    );
                }
            }
        };
    }

    private synchronized void startEventGeneratorIfNeeded() {
        if (generatorStarted) {
            return;
        }

        generatorStarted = true;

        Thread generatorThread = new Thread(() -> {
            Random random = new Random();

            while (true) {
                try {
                    Thread.sleep(2000);

                    PokemonEvent event = generateRandomPokemonEvent(random);

                    System.out.println(
                            "Generated event: "
                                    + event.getEventType()
                                    + " " + event.getPokemonName()
                                    + " in " + event.getCity()
                                    + ", level=" + event.getLevel()
                    );

                    dispatchEvent(event);

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });

        generatorThread.setDaemon(true);
        generatorThread.start();

        System.out.println("Global Pokemon event generator started");
    }

    private PokemonEvent generateRandomPokemonEvent(Random random) {
        List<String> pokemonNames = List.of(
                "Pikachu",
                "Charmander",
                "Squirtle",
                "Bulbasaur",
                "Snorlax",
                "Gengar",
                "Dragonite"
        );

        List<String> cities = List.of(
                "Krakow",
                "Warsaw"
        );

        List<EventType> eventTypes = List.of(
                EventType.RAID,
                EventType.SPAWN
//                EventType.COMMUNITY_DAY,
//                EventType.EGG
        );

        List<PokemonType> pokemonTypes = List.of(
                PokemonType.FIRE,
                PokemonType.WATER,
                PokemonType.ELECTRIC,
                PokemonType.GRASS
        );

        String pokemonName = pokemonNames.get(random.nextInt(pokemonNames.size()));
        String city = cities.get(random.nextInt(cities.size()));
        EventType eventType = eventTypes.get(random.nextInt(eventTypes.size()));
        PokemonType pokemonType = pokemonTypes.get(random.nextInt(pokemonTypes.size()));
        int level = random.nextInt(5) + 1;

        return PokemonEvent.newBuilder()
                .setEventId(nextEventId.getAndIncrement())
                .setEventType(eventType)
                .setCity(city)
                .setPokemonName(pokemonName)
                .addTypes(pokemonType)
                .setLevel(level)
                .build();
    }

    private void dispatchEvent(PokemonEvent event) {
        for (Map.Entry<Integer, UserSubscription> entry : subscriptions.entrySet()) {
            int subscriptionId = entry.getKey();
            UserSubscription userSubscription = entry.getValue();

            if (!matches(userSubscription.subscribe(), event)) {
                continue;
            }

            Notification notification = Notification.newBuilder()
                    .setNotificationId(nextNotificationId.getAndIncrement())
                    .setSubscriptionId(subscriptionId)
                    .setEvent(event)
                    .build();

            sendNotification(userSubscription.userId(), notification);
        }
    }

    private boolean matches(Subscribe subscribe, PokemonEvent event) {
        if (subscribe.getEvent() != event.getEventType()) {
            return false;
        }

        if (!subscribe.getCity().equalsIgnoreCase(event.getCity())) {
            return false;
        }

        if (event.getLevel() < subscribe.getMinLevel()) {
            return false;
        }

        if (subscribe.getTypesCount() == 0) {
            return true;
        }

        for (PokemonType eventType : event.getTypesList()) {
            if (subscribe.getTypesList().contains(eventType)) {
                return true;
            }
        }

        return false;
    }

    private void sendNotification(int userId, Notification notification) {
        pendingNotifications
                .computeIfAbsent(userId, id -> new CopyOnWriteArrayList<>())
                .add(notification);

        StreamObserver<PokemonResponse> client = activeClients.get(userId);

        if (client == null) {
            System.out.println(
                    "User " + userId
                            + " is offline, buffered notification_id="
                            + notification.getNotificationId()
            );
            return;
        }

        try {
            client.onNext(
                    PokemonResponse.newBuilder()
                            .setNotification(notification)
                            .build()
            );

            System.out.println(
                    "Sent notification_id="
                            + notification.getNotificationId()
                            + " to user=" + userId
                            + ", subscription_id=" + notification.getSubscriptionId()
            );

        } catch (Exception e) {
            activeClients.remove(userId);

            System.out.println(
                    "Could not send notification_id="
                            + notification.getNotificationId()
                            + " to user=" + userId
                            + ". Message stays buffered."
            );
        }
    }

    private void sendError(StreamObserver<PokemonResponse> responseObserver, String message) {
        ServerError error = ServerError.newBuilder()
                .setCode(ErrorCode.INVALID_REQUEST)
                .setMessage(message)
                .build();

        responseObserver.onNext(
                PokemonResponse.newBuilder()
                        .setError(error)
                        .build()
        );
    }
}