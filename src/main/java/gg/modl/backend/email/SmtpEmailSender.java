package gg.modl.backend.email;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

@RequiredArgsConstructor
public class SmtpEmailSender implements EmailSender {
    private final JavaMailSender mailSender;
    private final EmailConfiguration config;

    @Override
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
