package gg.modl.backend.email;

import jakarta.mail.MessagingException;
import java.io.UnsupportedEncodingException;

public interface EmailSender {
    void send(String toEmail, String subject, String htmlBody) throws MessagingException, UnsupportedEncodingException;
}
