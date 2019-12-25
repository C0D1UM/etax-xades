
import com.rabbitmq.client.*;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;
//import org.apache.log4j.Logger;
//import org.apache.log4j.BasicConfigurator;

public class XadesBesVerifyMain {

	private static String trustStoreType;
	private static String trustStorePath;
	private static String trustStorePassword;
	private static String certStoreDir;

	private static final String CONFIG_FILE_PATH = "src/main/resources/conf/etax-xades.properties";

	private static final String QUEUE_NAME = "services.xades.verify";
	private static final String HOST = System.getenv("HOST");
	private static final int PORT = Integer.parseInt(System.getenv("PORT"));
	private static final String USERNAME = System.getenv("USERNAME");
	private static final String PASSWORD = System.getenv("PASSWORD");
	
	//static Logger logger = Logger.getLogger(XadesBesVerifyMain.class);
	
	public static void main(String[] args) {

		//BasicConfigurator.configure();
		
		XadesBesVerifier verifier = new XadesBesVerifier();
		try {
			loadConfig(CONFIG_FILE_PATH);

			ConnectionFactory factory = new ConnectionFactory();
			factory.setHost(HOST);
			factory.setPort(PORT);
			factory.setUsername(USERNAME);
			factory.setPassword(PASSWORD);

			try (Connection connection = factory.newConnection();
				 Channel channel = connection.createChannel()) {
				channel.queueDeclare(QUEUE_NAME, false, false, false, null);
				channel.queuePurge(QUEUE_NAME);

				channel.basicQos(1);

				System.out.println("Running...");

				Object monitor = new Object();
				DeliverCallback deliverCallback = (consumerTag, delivery) -> {
					AMQP.BasicProperties replyProps = new AMQP.BasicProperties
							.Builder()
							.correlationId(delivery.getProperties().getCorrelationId())
							.build();

					boolean result = false;
					try {
						byte[] message = delivery.getBody();

						try {
							result = verifier.verifyBes(message, trustStoreType, trustStorePath, trustStorePassword, certStoreDir);
						} catch (Exception ignored) {
						}

					} catch (RuntimeException ignored) {
					} finally {
						final String response = Boolean.toString(result);
						channel.basicPublish("", delivery.getProperties().getReplyTo(), replyProps, response.getBytes(StandardCharsets.UTF_8));
						channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
						// RabbitMq consumer worker thread notifies the RPC server owner thread
						synchronized (monitor) {
							monitor.notify();
						}
					}
				};

				channel.basicConsume(QUEUE_NAME, false, deliverCallback, (consumerTag -> { }));
				// Wait and be prepared to consume the message from RPC client.
				while (true) {
					synchronized (monitor) {
						try {
							monitor.wait();
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					}
				}
			}
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		System.out.println("==============\tFinish\t==============");
	}

	private static void loadConfig(String configPath) {
		try {
			Properties prop = new Properties();
			InputStream config = new FileInputStream(configPath);
			// load the properties file
			prop.load(config);

//			verifyInput = prop.getProperty("VERIFY_INPUT_PATH");
			trustStoreType = prop.getProperty("TRUST_STORE_TYPE");
			trustStorePath = prop.getProperty("TRUST_STORE_PATH");
			trustStorePassword = prop.getProperty("TRUST_STORE_PASSWORD");
			certStoreDir = prop.getProperty("CERT_STORE_DIR");

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
