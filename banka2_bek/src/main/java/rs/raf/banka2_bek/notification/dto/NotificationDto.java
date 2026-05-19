package rs.raf.banka2_bek.notification.dto;

import lombok.*;

import java.time.LocalDateTime;

// [B1 — DONE] Response DTO for GET /notifications and PATCH /{id}/read.
// All fields are implemented: id, type (NotificationType.name()), title, body,
// read, createdAt, referenceType (nullable), referenceId (nullable).
//
// [B4/B5/B8] The type field is a plain String (enum name) so the frontend can
// switch on it to render type-specific icons or deep-links using referenceType/referenceId.

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationDto {

    private Long id;
    private String type;
    private String title;
    private String body;
    private boolean read;
    private LocalDateTime createdAt;
    private String referenceType;
    private Long referenceId;

}
