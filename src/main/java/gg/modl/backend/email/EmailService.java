package gg.modl.backend.email;

import gg.modl.backend.exception.ExternalServiceException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {
    private final JavaMailSender mailSender;
    private final EmailConfiguration config;

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
        MimeMessage mimeMessage = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, "UTF-8");

        helper.setFrom(config.getFromEmailAddress(), config.getFromName());
        helper.setTo(toEmail);
        helper.setSubject(subject);
        helper.setText(htmlBody, true);

        mailSender.send(mimeMessage);
    }
}
