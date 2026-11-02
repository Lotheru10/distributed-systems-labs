import com.rabbitmq.client.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;


public class Agencja {
    public static void main(String[] argv) throws Exception {
        final Set<String> TASKS = Set.of("person", "cargo", "satellite");
        AtomicInteger id = new AtomicInteger(1);
        // info
        System.out.println("AGENCY");

        // connection & channel
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");
        Connection connection = factory.newConnection();
        Channel channel = connection.createChannel();

        // exchange
        String EXCHANGE_NAME = "cosmos";
        channel.exchangeDeclare(EXCHANGE_NAME, BuiltinExchangeType.TOPIC);

        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        System.out.println("Input agency name");
        String name = br.readLine();

        String QUEUE_NAME = "confirmations" + name;
        channel.queueDeclare(QUEUE_NAME, false, false, false, null);

        String QUEUE_ADMIN = "admin";
        channel.queueDeclare(QUEUE_ADMIN, false, false, false, null);

        String EXCHANGE_ADMIN_REC = "admin_rec";
        channel.exchangeDeclare(EXCHANGE_ADMIN_REC, BuiltinExchangeType.TOPIC);

        String adminQueue = channel.queueDeclare().getQueue();
        channel.queueBind(adminQueue, EXCHANGE_ADMIN_REC, "agencies");
        channel.queueBind(adminQueue, EXCHANGE_ADMIN_REC, "all");


        Consumer confConsumer = new DefaultConsumer(channel) {
            @Override
            public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
                String message = new String(body, "UTF-8");
                System.out.println("Received confirmation: " + message);
            }
        };
        channel.basicConsume(QUEUE_NAME, true, confConsumer);

        Consumer adminConsumer = new DefaultConsumer(channel) {
            @Override
            public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
                String message = new String(body, "UTF-8");
                System.out.println("Admin: " + message);
            }
        };
        channel.basicConsume(adminQueue, true, adminConsumer);
        while (true) {
            System.out.println("Add task (person, cargo or satellite)");
            String key = br.readLine();
            if (!TASKS.contains(key)) {
                System.out.println("Unhandled task, please enter person, cargo or satellite");
                continue;
            }
            int currentId = id.getAndAdd(1);
            String stringId = String.valueOf(currentId);
            String message = key+ " from " + name +" with id: " + stringId;
            AMQP.BasicProperties props = new AMQP.BasicProperties.Builder()
                    .replyTo(QUEUE_NAME)
                    .correlationId(stringId)
                    .build();
            channel.basicPublish(EXCHANGE_NAME, key, props, message.getBytes(StandardCharsets.UTF_8));
            System.out.println("Sent: " + key + " task with id " + currentId);

            String adminMessage = name + " :" + message;
            channel.basicPublish("", QUEUE_ADMIN, null, adminMessage.getBytes(StandardCharsets.UTF_8));
        }

    }
}
