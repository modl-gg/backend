package gg.modl.backend.email;

import gg.modl.backend.infrastructure.config.ModlProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

@Configuration
@Slf4j
public class EmailSenderConfig {

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

        return new SmtpEmailSender(mailSenderProvider.getObject(), emailConfiguration);
    }
}
