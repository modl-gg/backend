package gg.modl.backend.staff.service;

import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.DynamicMongoTemplateProvider;
import gg.modl.backend.email.EmailService;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.staff.data.Invitation;
import gg.modl.backend.staff.data.Staff;
import gg.modl.backend.staff.dto.request.InviteStaffRequest;
import gg.modl.backend.staff.dto.response.InviteResultResponse;
import gg.modl.backend.staff.dto.response.StaffResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class InvitationService {
    private final DynamicMongoTemplateProvider mongoProvider;
    private final EmailService emailService;

    @Value("${modl.domain:modl.gg}")
    private String appDomain;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final long INVITATION_EXPIRY_MS = 24 * 60 * 60 * 1000; // 24 hours

    public InviteResultResponse sendInvitations(Server server, InviteStaffRequest request, String inviterEmail) {
        MongoTemplate template = getTemplate(server);

        List<String> emailsToInvite = new ArrayList<>();
        if (request.emails() != null && !request.emails().isEmpty()) {
            emailsToInvite.addAll(request.emails());
        } else if (request.email() != null) {
            emailsToInvite.add(request.email());
        }

        if (emailsToInvite.isEmpty()) {
            throw new IllegalArgumentException("No emails provided");
        }

        List<String> success = new ArrayList<>();
        List<InviteResultResponse.FailedInvite> failed = new ArrayList<>();

        for (String email : emailsToInvite) {
            try {
                processInvitation(template, server, email, request.role(), failed);
                if (failed.stream().noneMatch(f -> f.email().equals(email))) {
                    success.add(email);
                }
            } catch (Exception e) {
                log.error("Error processing invitation for {}: {}", email, e.getMessage());
                failed.add(new InviteResultResponse.FailedInvite(email, "Internal server error"));
            }
        }

        String message;
        if (success.isEmpty()) {
            message = "No invitations were sent successfully.";
        } else if (failed.isEmpty()) {
            message = success.size() == 1 ? "Invitation sent successfully." :
                    success.size() + " invitations sent successfully.";
        } else {
            message = success.size() + " invitation(s) sent successfully, " + failed.size() + " failed.";
        }

        return new InviteResultResponse(message, success, failed);
    }

    private void processInvitation(MongoTemplate template, Server server, String email, String role,
                                   List<InviteResultResponse.FailedInvite> failed) {
        // Check if admin email
        if (server.getAdminEmail() != null && email.equalsIgnoreCase(server.getAdminEmail())) {
            failed.add(new InviteResultResponse.FailedInvite(email, "Cannot send invitation to the admin email address."));
            return;
        }

        // Check if user already exists
        Query staffQuery = Query.query(Criteria.where("email").is(email));
        if (template.exists(staffQuery, Staff.class, CollectionName.STAFF)) {
            failed.add(new InviteResultResponse.FailedInvite(email, "Email is already associated with an existing user."));
            return;
        }

        // Check if invitation already pending
        Query invQuery = Query.query(Criteria.where("email").is(email).and("status").is("pending"));
        if (template.exists(invQuery, Invitation.class, CollectionName.INVITATIONS)) {
            failed.add(new InviteResultResponse.FailedInvite(email, "An invitation for this email is already pending."));
            return;
        }

        // Create invitation
        String token = generateToken();
        Date expiresAt = new Date(System.currentTimeMillis() + INVITATION_EXPIRY_MS);

        Invitation invitation = Invitation.builder()
                .email(email)
                .role(role)
                .token(token)
                .expiresAt(expiresAt)
                .status("pending")
                .createdAt(new Date())
                .updatedAt(new Date())
                .build();

        template.save(invitation, CollectionName.INVITATIONS);

        // Send email
        String invitationLink = String.format("https://%s.%s/accept-invitation?token=%s",
                server.getCustomDomain(), appDomain, token);

        try {
            emailService.sendStaffInviteEmail(
                    email,
                    server.getServerName(),
                    role,
                    invitationLink
            );
        } catch (Exception e) {
            log.error("Failed to send invitation email to {}: {}", email, e.getMessage());
            // Remove the invitation since email failed
            template.remove(Query.query(Criteria.where("_id").is(invitation.getId())), Invitation.class, CollectionName.INVITATIONS);
            failed.add(new InviteResultResponse.FailedInvite(email, "Failed to send invitation email."));
        }
    }

    public boolean resendInvitation(Server server, String invitationId) {
        MongoTemplate template = getTemplate(server);

        Query query = Query.query(Criteria.where("_id").is(invitationId));
        Invitation invitation = template.findOne(query, Invitation.class, CollectionName.INVITATIONS);

        if (invitation == null) {
            return false;
        }

        // Generate new token and expiry
        String newToken = generateToken();
        Date newExpiry = new Date(System.currentTimeMillis() + INVITATION_EXPIRY_MS);

        Update update = new Update()
                .set("token", newToken)
                .set("expiresAt", newExpiry)
                .set("updatedAt", new Date());

        template.updateFirst(query, update, Invitation.class, CollectionName.INVITATIONS);

        // Send email
        String invitationLink = String.format("https://%s.%s/accept-invitation?token=%s",
                server.getCustomDomain(), appDomain, newToken);

        emailService.sendStaffInviteEmail(
                invitation.getEmail(),
                server.getServerName(),
                invitation.getRole(),
                invitationLink
        );

        return true;
    }

    public StaffResponse acceptInvitation(Server server, String token) {
        MongoTemplate template = getTemplate(server);

        Query invQuery = Query.query(Criteria.where("token").is(token).and("status").is("pending"));
        Invitation invitation = template.findOne(invQuery, Invitation.class, CollectionName.INVITATIONS);

        if (invitation == null) {
            throw new IllegalArgumentException("Invalid or expired invitation token.");
        }

        if (invitation.getExpiresAt() != null && invitation.getExpiresAt().before(new Date())) {
            throw new IllegalArgumentException("This invitation has expired. Please request a new invitation.");
        }

        Query existingStaffQuery = Query.query(Criteria.where("email").is(invitation.getEmail()));
        if (template.exists(existingStaffQuery, Staff.class, CollectionName.STAFF)) {
            throw new IllegalArgumentException("A staff member with this email already exists.");
        }

        String username = generateUsernameFromEmail(invitation.getEmail());
        String uniqueUsername = ensureUniqueUsername(template, username);

        Date now = new Date();
        Staff newStaff = Staff.builder()
                .email(invitation.getEmail())
                .username(uniqueUsername)
                .role(invitation.getRole())
                .createdAt(now)
                .updatedAt(now)
                .build();

        template.save(newStaff, CollectionName.STAFF);

        Update update = new Update()
                .set("status", "accepted")
                .set("updatedAt", now);
        template.updateFirst(invQuery, update, Invitation.class, CollectionName.INVITATIONS);

        return new StaffResponse(
                newStaff.getId(),
                newStaff.getEmail(),
                newStaff.getUsername(),
                newStaff.getRole(),
                "active",
                newStaff.getAssignedMinecraftUuid(),
                newStaff.getAssignedMinecraftUsername(),
                newStaff.getCreatedAt()
        );
    }

    private String generateUsernameFromEmail(String email) {
        String localPart = email.split("@")[0];
        return localPart.replaceAll("[^a-zA-Z0-9_]", "_");
    }

    private String ensureUniqueUsername(MongoTemplate template, String baseUsername) {
        String username = baseUsername;
        int counter = 1;

        while (template.exists(Query.query(Criteria.where("username").is(username)), Staff.class, CollectionName.STAFF)) {
            username = baseUsername + counter;
            counter++;
        }

        return username;
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private MongoTemplate getTemplate(Server server) {
        return mongoProvider.getFromDatabaseName(server.getDatabaseName());
    }
}
