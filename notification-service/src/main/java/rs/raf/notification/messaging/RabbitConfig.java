package rs.raf.notification.messaging;

import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import rs.raf.banka2.contracts.NotificationRabbit;

/**
 * RabbitMQ konfiguracija za notification-service.
 *
 * <p><b>BE-NTF-01 (manual ack + DLQ):</b> da bismo izbegli silent message loss
 * (default ack-on-listener-return je gutao transient SMTP greske), ovde:
 * <ul>
 *   <li>Email queue je vezana za DLX (dead-letter exchange) {@code notification.dlx};
 *       poruke koje se permanentno odbiju zavrsavaju u {@code notification.email.dlq}.</li>
 *   <li>Listener container koristi {@code AcknowledgeMode.MANUAL} — consumer
 *       eksplicitno odlucuje ack/nack/requeue na osnovu tipa greske.</li>
 *   <li>{@code defaultRequeueRejected=false} osigurava da nack bez requeue
 *       routira poruku na DLX (po Rabbit semantici).</li>
 * </ul>
 */
@Configuration
public class RabbitConfig {

    /** Dead-letter exchange za odbacene email notification poruke. */
    public static final String DLX_EXCHANGE = "notification.dlx";

    /** Dead-letter queue: drzi poruke koje ne mogu da se procesiraju permanentno. */
    public static final String DLQ_NAME = "notification.email.dlq";

    /** Routing key za DLX. */
    public static final String DLQ_ROUTING_KEY = "notification.email.dlq";

    @Bean
    public TopicExchange banka2EventsExchange() {
        return new TopicExchange(NotificationRabbit.EXCHANGE, true, false);
    }

    @Bean
    public Queue emailQueue() {
        // durable=true: poruke prezive restart broker-a / consumer-a.
        // BE-NTF-01: linkuj DLX za poruke koje consumer permanentno odbije
        // (nack bez requeue) — ne gube se, ali ne ostaju ni u glavnom queue-u.
        return QueueBuilder.durable(NotificationRabbit.EMAIL_QUEUE)
                .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", DLQ_ROUTING_KEY)
                .build();
    }

    @Bean
    public Binding emailBinding(Queue emailQueue, TopicExchange banka2EventsExchange) {
        return BindingBuilder.bind(emailQueue).to(banka2EventsExchange).with(NotificationRabbit.EMAIL_ROUTING_KEY);
    }

    @Bean
    public DirectExchange notificationDlx() {
        return new DirectExchange(DLX_EXCHANGE, true, false);
    }

    @Bean
    public Queue emailDeadLetterQueue() {
        return QueueBuilder.durable(DLQ_NAME).build();
    }

    @Bean
    public Binding emailDlqBinding(Queue emailDeadLetterQueue, DirectExchange notificationDlx) {
        return BindingBuilder.bind(emailDeadLetterQueue).to(notificationDlx).with(DLQ_ROUTING_KEY);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    /**
     * BE-NTF-01: manual-ack listener factory. Consumer mora eksplicitno da
     * basicAck / basicNack(requeue=true) / basicNack(requeue=false → DLQ).
     */
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory, MessageConverter messageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        factory.setDefaultRequeueRejected(false);
        return factory;
    }
}
