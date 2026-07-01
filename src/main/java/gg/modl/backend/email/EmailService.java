package gg.modl.backend.email;

import gg.modl.backend.infrastructure.exception.ExternalServiceException;
import jakarta.mail.MessagingException;
import java.io.UnsupportedEncodingException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailService {
    private final EmailSender emailSender;

    public void sendStaffInviteEmail(String toEmail, String serverName, String role, String invitationLink) {
        try {
            EmailHTMLTemplate.HTMLEmail email = EmailHTMLTemplate.STAFF_INVITE_TEMPLATE.build(serverName, role, invitationLink);
            send(toEmail, email);
        } catch (Exception e) {
            throw new ExternalServiceException("Failed to send staff invitation email", e);
        }
    }

    public void send(String toEmail, EmailHTMLTemplate.HTMLEmail email) throws MessagingException, UnsupportedEncodingException {
        send(toEmail, email.subject(), email.body());
    }

    public void send(String toEmail, String subject, String htmlBody) throws MessagingException, UnsupportedEncodingException {
        emailSender.send(toEmail, subject, htmlBody);
    }
}
