import com.rabbitmq.client.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;


public class Admin {
    public static void main(String[] argv) throws Exception {
        final Set<String> TASKS = Set.of("carriers", "agencies", "all");
        // info
        System.out.println("ADMIN");

        // connection & channel
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");
        Connection connection = factory.newConnection();
        Channel channel = connection.createChannel();

        // exchange
        String EXCHANGE_ADMIN_REC = "admin_rec";
        channel.exchangeDeclare(EXCHANGE_ADMIN_REC, BuiltinExchangeType.TOPIC);


        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        String QUEUE_NAME = "admin";
        channel.queueDeclare(QUEUE_NAME, false, false, false, null);
        channel.queuePurge(QUEUE_NAME);

        Consumer consumer = new DefaultConsumer(channel) {
            @Override
            public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
                String message = new String(body, "UTF-8");
                System.out.println(message);
            }
        };
        channel.basicConsume(QUEUE_NAME, true, consumer);
        while (true) {
            String task = br.readLine();
            if (!TASKS.contains(task)) {
                System.out.println("Unhandled task, please enter carriers, agencies or all");
                continue;
            }
            System.out.println("Enter message: ");
            String message = br.readLine();
            channel.basicPublish(EXCHANGE_ADMIN_REC, task, null, message.getBytes(StandardCharsets.UTF_8));
            System.out.println("Sent: " + message);

        }
    }
}
