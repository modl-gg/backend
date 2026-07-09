package gg.modl.backend.email;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailService {
    private final AsyncEmailDispatcher dispatcher;

    public void sendStaffInviteEmail(String toEmail, String serverName, String role, String invitationLink) {
        EmailHTMLTemplate.HTMLEmail email = EmailHTMLTemplate.STAFF_INVITE_TEMPLATE.build(serverName, role, invitationLink);
        send(toEmail, email);
    }

    public void send(String toEmail, EmailHTMLTemplate.HTMLEmail email) {
        send(toEmail, email.subject(), email.body());
    }

    public void send(String toEmail, String subject, String htmlBody) {
        dispatcher.dispatch(toEmail, subject, htmlBody);
    }
}
