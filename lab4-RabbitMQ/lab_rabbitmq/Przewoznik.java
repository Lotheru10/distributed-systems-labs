import com.rabbitmq.client.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class Przewoznik {
    public static void main(String[] argv) throws Exception {

        // info
        System.out.println("Carrier");

        // connection & channel
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");
        Connection connection = factory.newConnection();
        Channel channel = connection.createChannel();

        // exchange
        String EXCHANGE_NAME = "cosmos";
        channel.exchangeDeclare(EXCHANGE_NAME, BuiltinExchangeType.TOPIC);

        String QUEUE_ADMIN = "admin";
        channel.queueDeclare(QUEUE_ADMIN, false, false, false, null);

        String EXCHANGE_ADMIN_REC = "admin_rec";
        channel.exchangeDeclare(EXCHANGE_ADMIN_REC, BuiltinExchangeType.TOPIC);

        String adminQueue = channel.queueDeclare().getQueue();
        channel.queueBind(adminQueue, EXCHANGE_ADMIN_REC, "carriers");
        channel.queueBind(adminQueue, EXCHANGE_ADMIN_REC, "all");


        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        System.out.println("Enter keys - two out of following: person, cargo and satellite");
        String line = br.readLine();
        String[] keys = line.split(" ");
        if (keys.length != 2) {
            System.out.println("Invalid keys! Try again");
        }
        for (String key : keys) {
            String queueName = "task: " + key;
            channel.queueDeclare(queueName, false, false, false, null);
            channel.queueBind(queueName, EXCHANGE_NAME, key);

            channel.basicQos(1);


            // consumer (message handling)
            Consumer consumer = new DefaultConsumer(channel) {
                @Override
                public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
                    String message = new String(body, "UTF-8");
                    System.out.println("Received: " + message);
                    try {
                        Thread.sleep(5000L);
                        channel.basicAck(envelope.getDeliveryTag(), false);
                        String confirmation = "Done: " + message;
                        AMQP.BasicProperties props = new AMQP.BasicProperties.Builder()
                                .correlationId(properties.getCorrelationId())
                                .build();
                        channel.basicPublish("", properties.getReplyTo(), props, confirmation.getBytes(StandardCharsets.UTF_8));
                        channel.basicPublish("", QUEUE_ADMIN, null, confirmation.getBytes(StandardCharsets.UTF_8));
                    } catch (InterruptedException e) {
                        channel.basicNack(envelope.getDeliveryTag(), false, true);
                    }
                }
            };
            // start listening
            channel.basicConsume(queueName, false, consumer);
        }
        Consumer adminConsumer = new DefaultConsumer(channel) {
            @Override
            public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
                String message = new String(body, "UTF-8");
                System.out.println("Admin: " + message);
            }
        };
        channel.basicConsume(adminQueue, false, adminConsumer);
        System.out.println("Waiting for tasks");
    }
}
