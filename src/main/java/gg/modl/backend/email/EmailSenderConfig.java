package gg.modl.backend.email;

import gg.modl.backend.infrastructure.config.ModlProperties;
import java.util.Properties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

@Configuration
@Slf4j
public class EmailSenderConfig {
    private static final String SMTP_TIMEOUT_MILLIS = "10000";

    @Bean
    public EmailSender emailSender(
        ObjectProvider<JavaMailSender> mailSenderProvider,
        EmailConfiguration emailConfiguration,
        ModlProperties modlProperties
    ) {
        if (modlProperties.isDevelopmentMode()) {
            log.warn("Development mode is on; emails will be logged to the console instead of delivered.");
            return new LoggingEmailSender();
        }

        JavaMailSender mailSender = mailSenderProvider.getObject();
        applySendTimeouts(mailSender);
        return new SmtpEmailSender(mailSender, emailConfiguration);
    }

    private void applySendTimeouts(JavaMailSender mailSender) {
        if (mailSender instanceof JavaMailSenderImpl impl) {
            Properties props = impl.getJavaMailProperties();
            props.putIfAbsent("mail.smtp.connectiontimeout", SMTP_TIMEOUT_MILLIS);
            props.putIfAbsent("mail.smtp.timeout", SMTP_TIMEOUT_MILLIS);
            props.putIfAbsent("mail.smtp.writetimeout", SMTP_TIMEOUT_MILLIS);
        }
    }
}
