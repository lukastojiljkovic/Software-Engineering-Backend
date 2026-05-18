package rs.raf.banka2.contracts;

/** Deljene RabbitMQ topologija konstante za notifikacioni kanal. */
public final class NotificationRabbit {

    private NotificationRabbit() {
    }

    /** Topic exchange za sve domenske evente Banke 2. */
    public static final String EXCHANGE = "banka2.events";

    /** Routing key za email notifikacije. */
    public static final String EMAIL_ROUTING_KEY = "notification.email";

    /** Durable queue koji notification-service konzumira. */
    public static final String EMAIL_QUEUE = "notification.email.q";
}
