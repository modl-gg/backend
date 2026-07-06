package gg.modl.backend.email;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AsyncEmailDispatcher {
    private final EmailSender emailSender;

    @Async("emailTaskExecutor")
    public void dispatch(String toEmail, String subject, String htmlBody) {
        try {
            emailSender.send(toEmail, subject, htmlBody);
        } catch (Exception e) {
            log.error("Failed to send email to {}", EmailAddressUtil.mask(toEmail), e);
        }
    }
}
