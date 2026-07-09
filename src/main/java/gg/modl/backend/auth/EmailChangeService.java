package gg.modl.backend.auth;

import gg.modl.backend.auth.session.AuthSessionData;
import gg.modl.backend.auth.session.SessionService;
import gg.modl.backend.billing.service.BillingService;
import gg.modl.backend.email.EmailAddressUtil;
import gg.modl.backend.email.EmailHTMLTemplate;
import gg.modl.backend.email.EmailService;
import gg.modl.backend.infrastructure.exception.ConflictException;
import gg.modl.backend.infrastructure.exception.ResourceNotFoundException;
import gg.modl.backend.infrastructure.exception.ValidationException;
import gg.modl.backend.role.service.PermissionService;
import gg.modl.backend.server.ServerService;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.staff.data.Staff;
import gg.modl.backend.staff.service.StaffService;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailChangeService {
    private final PermissionService permissionService;
    private final AuthService authService;
    private final StaffService staffService;
    private final ServerService serverService;
    private final WebAuthnService webAuthnService;
    private final BillingService billingService;
    private final SessionService sessionService;
    private final EmailService emailService;

    public void sendChangeCode(Server server, String currentEmail, String newEmail) {
        boolean isSuperAdmin = permissionService.isSuperAdmin(server, currentEmail);
        String normalizedNewEmail = validateTarget(server, currentEmail, newEmail, isSuperAdmin);

        authService.sendEmailChangeCode(server, normalizedNewEmail);
    }

    public AuthSessionData changeEmail(Server server, String currentEmail, String newEmail, String code,
                                       String clientIp, String userAgent) {
        boolean isSuperAdmin = permissionService.isSuperAdmin(server, currentEmail);
        String normalizedNewEmail = validateTarget(server, currentEmail, newEmail, isSuperAdmin);

        if (!authService.verifyCode(server, normalizedNewEmail, code)) {
            throw new ValidationException("Invalid or expired verification code.");
        }

        if (isSuperAdmin) {
            serverService.changeAdminEmail(server, normalizedNewEmail);
        }

        Optional<Staff> staff = staffService.applyStaffEmailChange(server, currentEmail, normalizedNewEmail);
        if (staff.isEmpty() && !isSuperAdmin) {
            throw new ResourceNotFoundException("Staff member not found");
        }

        revokeStalePasskeys(server, currentEmail);
        if (isSuperAdmin) {
            billingService.syncCustomerEmail(server, normalizedNewEmail);
        }

        sessionService.invalidateAllSessionsForEmail(server, currentEmail);
        AuthSessionData newSession = sessionService.createSession(server, normalizedNewEmail, clientIp, userAgent);

        notifyPreviousAddress(server, currentEmail, normalizedNewEmail);
        return newSession;
    }

    private String validateTarget(Server server, String currentEmail, String newEmail, boolean isSuperAdmin) {
        String normalizedNewEmail = EmailAddressUtil.normalizeIfValid(newEmail);
        if (normalizedNewEmail == null) {
            throw new ValidationException("A valid email address is required");
        }
        if (normalizedNewEmail.equalsIgnoreCase(currentEmail)) {
            throw new ValidationException("New email must be different from your current email.");
        }
        boolean collidesWithServerAdmin = server.getAdminEmail() != null
            && normalizedNewEmail.equalsIgnoreCase(server.getAdminEmail());
        if (collidesWithServerAdmin
            || staffService.isStaffEmailInUse(server, normalizedNewEmail, currentEmail)
            || (isSuperAdmin && serverService.isAdminEmailInUse(normalizedNewEmail, server.getId()))) {
            throw new ConflictException("Email address already in use");
        }
        return normalizedNewEmail;
    }

    private void revokeStalePasskeys(Server server, String previousEmail) {
        try {
            webAuthnService.deleteCredentialsForEmail(server, previousEmail);
        } catch (Exception e) {
            log.warn("Failed to remove passkeys for the previous email after an email change on server {}", server.getId(), e);
        }
    }

    private void notifyPreviousAddress(Server server, String previousEmail, String newEmail) {
        try {
            emailService.send(previousEmail,
                EmailHTMLTemplate.EMAIL_CHANGED_NOTICE.build(server.getServerName(), EmailAddressUtil.mask(newEmail)));
        } catch (Exception e) {
            log.warn("Failed to send email-change notice to the previous address on server {}", server.getId(), e);
        }
    }
}
