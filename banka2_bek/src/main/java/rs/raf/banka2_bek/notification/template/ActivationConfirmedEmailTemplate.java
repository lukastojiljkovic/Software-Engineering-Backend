package rs.raf.banka2_bek.notification.template;

import org.springframework.stereotype.Component;

@Component
public class ActivationConfirmedEmailTemplate {

    public String buildSubject() {
        return "Banka 2 account activated";
    }

    public String buildBody(String firstName) {
        String greeting = (firstName != null && !firstName.isBlank()) ? firstName : "there";
        return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                    <meta charset="UTF-8">
                    <title>Account activated</title>
                </head>
                <body style="margin:0;padding:0;background-color:#f8fafc;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;">
                <table role="presentation" cellpadding="0" cellspacing="0" width="100%%" style="padding:32px 0;">
                    <tr>
                        <td align="center">
                            <table role="presentation" cellpadding="0" cellspacing="0" width="100%%" style="max-width:520px;background-color:#ffffff;border-radius:16px;overflow:hidden;box-shadow:0 18px 45px rgba(15,23,42,0.18);border:1px solid #e5e7eb;">
                                <tr>
                                    <td style="background:linear-gradient(135deg,#16a34a,#15803d);padding:18px 24px;color:#e5e7eb;text-align:center;">
                                        <h1 style="margin:0;font-size:20px;font-weight:600;letter-spacing:0.02em;">Banka 2 - Account activated</h1>
                                    </td>
                                </tr>
                                <tr>
                                    <td style="padding:24px;text-align:center;">
                                        <p style="margin:0 0 16px 0;font-size:14px;color:#4b5563;">Hi %s,</p>
                                        <p style="margin:0 0 12px 0;font-size:14px;color:#4b5563;">
                                            Your Banka 2 account has been activated successfully.
                                        </p>
                                        <p style="margin:0 0 0 0;font-size:13px;color:#6b7280;">
                                            You can now log in with your email and password.
                                        </p>
                                    </td>
                                </tr>
                                <tr>
                                    <td style="padding:16px 24px;border-top:1px solid #e5e7eb;background-color:#f9fafb;">
                                        <p style="margin:0;font-size:11px;color:#9ca3af;text-align:center;">
                                            This is an automated message from Banka 2. Please do not reply to this email.
                                        </p>
                                    </td>
                                </tr>
                            </table>
                        </td>
                    </tr>
                </table>
                </body>
                </html>
                """.formatted(greeting);
    }
}
