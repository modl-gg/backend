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
        try {
            EmailHTMLTemplate.HTMLEmail email = EmailHTMLTemplate.STAFF_INVITE_TEMPLATE.build(serverName, role, invitationLink);
            send(toEmail, email);
        } catch (Exception e) {
            throw new RuntimeException("Failed to send staff invitation email", e);
        }
    }
}
