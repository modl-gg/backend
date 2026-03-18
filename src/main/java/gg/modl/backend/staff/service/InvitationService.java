package gg.modl.backend.staff.service;

import gg.modl.backend.config.ModlProperties;
import gg.modl.backend.exception.ConflictException;
import gg.modl.backend.exception.ValidationException;
import gg.modl.backend.database.mongo.repository.InvitationMongoRepository;
import gg.modl.backend.database.mongo.repository.StaffMongoRepository;
import gg.modl.backend.email.EmailService;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.data.ServerPlan;
import gg.modl.backend.staff.data.Invitation;
import gg.modl.backend.staff.data.Staff;
import gg.modl.backend.staff.dto.request.InviteStaffRequest;
import gg.modl.backend.staff.dto.response.InviteResultResponse;
import gg.modl.backend.staff.dto.response.StaffResponse;
import gg.modl.backend.util.IdGenerator;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class InvitationService {
    private final StaffMongoRepository staffRepository;
    private final InvitationMongoRepository invitationRepository;
    private final EmailService emailService;
    private final IdGenerator idGenerator;
    private final ModlProperties modlProperties;

    private static final long INVITATION_EXPIRY_MS = 24 * 60 * 60 * 1000;

    private static final int FREE_TIER_STAFF_LIMIT = 5;
    private static final int PREMIUM_TIER_STAFF_LIMIT = 100_000;

    public InviteResultResponse sendInvitations(Server server, InviteStaffRequest request, String inviterEmail) {
        List<String> emailsToInvite = new ArrayList<>();
        if (request.emails() != null && !request.emails().isEmpty()) {
            emailsToInvite.addAll(request.emails());
        } else if (request.email() != null) {
            emailsToInvite.add(request.email());
        }

        if (emailsToInvite.isEmpty()) {
            throw new ValidationException("No emails provided");
        }

        List<String> normalizedEmailsToInvite = emailsToInvite.stream()
            .filter(email -> email != null && !email.isBlank())
            .map(email -> email.trim().toLowerCase(Locale.ROOT))
            .distinct()
            .toList();

        if (normalizedEmailsToInvite.isEmpty()) {
            throw new ValidationException("No valid emails provided");
        }

        int staffLimit = server.getPlan() == ServerPlan.PREMIUM ? PREMIUM_TIER_STAFF_LIMIT : FREE_TIER_STAFF_LIMIT;
        long currentStaffCount = staffRepository.countAll(server);
        long pendingInvitationsCount = invitationRepository.countActive(server, new Date());
        long totalCurrentMembers = currentStaffCount + pendingInvitationsCount;

        if (totalCurrentMembers >= staffLimit) {
            String planName = server.getPlan() == ServerPlan.PREMIUM ? "Premium" : "Free";
            throw new ConflictException(
                String.format("Staff member limit reached. Your %s plan allows up to %d staff members. " +
                              "Please upgrade your plan or remove existing staff members to invite new ones.",
                    planName, staffLimit)
            );
        }

        int availableSlots = (int) (staffLimit - totalCurrentMembers);
        if (normalizedEmailsToInvite.size() > availableSlots) {
            throw new ConflictException(
                String.format("Cannot invite %d staff members. You only have %d available slot(s) remaining.",
                    normalizedEmailsToInvite.size(), availableSlots)
            );
        }

        List<String> success = new ArrayList<>();
        List<InviteResultResponse.FailedInvite> failed = new ArrayList<>();

        for (String email : normalizedEmailsToInvite) {
            try {
                processInvitation(server, email, request.role(), failed);
                if (failed.stream().noneMatch(f -> f.email().equals(email))) {
                    success.add(email);
                }
            } catch (Exception e) {
                log.error("Error processing invitation for {}", email, e);
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

    private void processInvitation(Server server, String email, String role,
                                   List<InviteResultResponse.FailedInvite> failed) {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);

        if (server.getAdminEmail() != null && normalizedEmail.equalsIgnoreCase(server.getAdminEmail())) {
            failed.add(new InviteResultResponse.FailedInvite(normalizedEmail, "Cannot send invitation to the admin email address."));
            return;
        }

        if (staffRepository.existsByEmailExact(server, normalizedEmail)) {
            failed.add(new InviteResultResponse.FailedInvite(normalizedEmail, "Email is already associated with an existing user."));
            return;
        }

        if (invitationRepository.existsByEmailActive(server, normalizedEmail, new Date())) {
            failed.add(new InviteResultResponse.FailedInvite(normalizedEmail, "An invitation for this email is already pending."));
            return;
        }

        String token = idGenerator.generateToken();
        Date expiresAt = new Date(System.currentTimeMillis() + INVITATION_EXPIRY_MS);

        Invitation invitation = Invitation.builder()
            .email(normalizedEmail)
            .role(role)
            .token(token)
            .expiresAt(expiresAt)
            .createdAt(new Date())
            .updatedAt(new Date())
            .build();

        invitationRepository.saveEntity(server, invitation);

        String invitationLink = String.format("https://%s.%s/accept-invitation?token=%s",
            server.getCustomDomain(), modlProperties.getDomain(), token);

        try {
            emailService.sendStaffInviteEmail(
                normalizedEmail,
                server.getServerName(),
                role,
                invitationLink
            );
        } catch (Exception e) {
            log.error("Failed to send invitation email to {}", normalizedEmail, e);
            invitationRepository.deleteById(server, invitation.getId());
            failed.add(new InviteResultResponse.FailedInvite(normalizedEmail, "Failed to send invitation email."));
        }
    }

    public boolean resendInvitation(Server server, String invitationId) {
        Invitation invitation = invitationRepository.findById(server, invitationId).orElse(null);

        if (invitation == null) {
            return false;
        }

        String newToken = idGenerator.generateToken();
        Date newExpiry = new Date(System.currentTimeMillis() + INVITATION_EXPIRY_MS);

        invitationRepository.refreshToken(server, invitationId, newToken, newExpiry, new Date());

        String invitationLink = String.format("https://%s.%s/accept-invitation?token=%s",
            server.getCustomDomain(), modlProperties.getDomain(), newToken);

        emailService.sendStaffInviteEmail(
            invitation.getEmail(),
            server.getServerName(),
            invitation.getRole(),
            invitationLink
        );

        return true;
    }

    public StaffResponse acceptInvitation(Server server, String token) {
        Invitation invitation = invitationRepository.findByToken(server, token).orElse(null);

        if (invitation == null) {
            throw new ValidationException("Invalid or expired invitation token.");
        }

        if (invitation.getExpiresAt() == null || invitation.getExpiresAt().before(new Date())) {
            throw new ValidationException("This invitation has expired. Please request a new invitation.");
        }

        if (staffRepository.existsByEmailExact(server, invitation.getEmail())) {
            throw new ConflictException("A staff member with this email already exists.");
        }

        String username = generateUsernameFromEmail(invitation.getEmail());
        String uniqueUsername = ensureUniqueUsername(server, username);

        Date now = new Date();
        Staff newStaff = Staff.builder()
            .email(invitation.getEmail())
            .username(uniqueUsername)
            .role(invitation.getRole())
            .createdAt(now)
            .updatedAt(now)
            .build();

        staffRepository.saveEntity(server, newStaff);

        invitationRepository.deleteById(server, invitation.getId());

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

    private String ensureUniqueUsername(Server server, String baseUsername) {
        String username = baseUsername;
        int counter = 1;

        while (staffRepository.existsByUsername(server, username)) {
            username = baseUsername + counter;
            counter++;
        }

        return username;
    }
}
