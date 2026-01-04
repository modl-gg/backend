package gg.modl.backend.email;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;

@Service
@RequiredArgsConstructor
public class EmailService {
    private final JavaMailSender mailSender;
    private final EmailConfiguration config;

    public void send(String toEmail, String subject, String htmlBody) throws MessagingException, UnsupportedEncodingException {
        MimeMessage mimeMessage = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, "UTF-8");

        helper.setFrom(config.getFromEmailAddress(), config.getFromName());
        helper.setTo(toEmail);
        helper.setSubject(subject);
        helper.setText(htmlBody, true);

        mailSender.send(mimeMessage);
    }

    public void send(String toEmail, EmailHTMLTemplate.HTMLEmail email) throws MessagingException, UnsupportedEncodingException {
        send(toEmail, email.subject(), email.body());
    }

    public void sendStaffInviteEmail(String toEmail, String serverName, String role, String invitationLink) {
        String subject = "You have been invited to join the " + serverName + " team!";
        String htmlBody = """
                <html>
                <body style="font-family: Arial, sans-serif; line-height: 1.6; color: #333;">
                    <h2>Staff Invitation</h2>
                    <p>You have been invited to join the <strong>%s</strong> team as a <strong>%s</strong>.</p>
                    <p>Click the button below to accept your invitation:</p>
                    <p style="margin: 20px 0;">
                        <a href="%s" style="display: inline-block; padding: 12px 24px; background-color: #4F46E5; color: white; text-decoration: none; border-radius: 6px;">Accept Invitation</a>
                    </p>
                    <p style="color: #666; font-size: 14px;">This invitation will expire in 24 hours.</p>
                    <p style="color: #666; font-size: 12px;">If you didn't expect this invitation, you can safely ignore this email.</p>
                </body>
                </html>
                """.formatted(serverName, role, invitationLink);

        try {
            send(toEmail, subject, htmlBody);
        } catch (Exception e) {
            throw new RuntimeException("Failed to send staff invitation email", e);
        }
    }
}
