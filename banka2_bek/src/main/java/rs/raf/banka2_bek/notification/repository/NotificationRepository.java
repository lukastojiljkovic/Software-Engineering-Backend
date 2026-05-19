package rs.raf.banka2_bek.notification.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import rs.raf.banka2_bek.notification.model.Notification;

/**
 * [B1] Spring Data JPA repository for the {@link Notification} entity.
 * All query methods are derived or custom-JPQL; no changes are needed by
 * dependent tasks — B4/B5/B8 interact exclusively through
 * {@link rs.raf.banka2_bek.notification.service.NotificationService#notify}.
 */
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /**
     * Returns a paginated list of all notifications for the given recipient,
     * sorted by the {@code Pageable} specification (typically {@code createdAt} desc).
     */
    Page<Notification> findByRecipientIdAndRecipientType(Long recipientId, String recipientType, Pageable pageable);

    /**
     * Returns a paginated list of notifications for the given recipient
     * filtered by read status.
     *
     * @param read {@code false} to fetch only unread notifications
     */
    Page<Notification> findByRecipientIdAndRecipientTypeAndRead(Long recipientId, String recipientType, boolean read, Pageable pageable);

    /**
     * Counts notifications for the given recipient filtered by read status.
     *
     * @param read pass {@code false} to count unread notifications
     * @return total matching count (used by the unread-count endpoint)
     */
    long countByRecipientIdAndRecipientTypeAndRead(Long recipientId, String recipientType, boolean read);

    /**
     * Bulk-marks all notifications for the given recipient as read.
     * Must be called inside a transaction ({@code @Transactional} on the caller).
     *
     * @return number of rows updated
     */
    @Modifying
    @Query("UPDATE Notification notification SET notification.read = true " +
            "WHERE notification.recipientId = :recipientId AND notification.recipientType = :recipientType")
    int markAllReadForRecipient(
            @Param("recipientId") Long recipientId,
            @Param("recipientType") String recipientType
    );
}
