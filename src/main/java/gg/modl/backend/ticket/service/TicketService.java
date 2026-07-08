package gg.modl.backend.ticket.service;

import gg.modl.backend.database.mongo.repository.StaffMongoRepository;
import gg.modl.backend.infrastructure.exception.ConflictException;
import gg.modl.backend.infrastructure.exception.ResourceNotFoundException;
import gg.modl.backend.database.mongo.repository.TicketMongoRepository;
import gg.modl.backend.email.EmailAddressUtil;
import gg.modl.backend.staff.data.Staff;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.settings.data.QuickResponseSettings;
import gg.modl.backend.settings.data.TicketFormSettings;
import gg.modl.backend.settings.service.QuickResponseSettingsService;
import gg.modl.backend.settings.service.TicketFormSettingsService;
import gg.modl.backend.settings.service.WebhookSettingsService;
import gg.modl.backend.ticket.data.AppealWorkflowStatus;
import gg.modl.backend.ticket.data.Ticket;
import gg.modl.backend.ticket.data.TicketCategory;
import gg.modl.backend.ticket.data.TicketNote;
import gg.modl.backend.ticket.data.TicketPriority;
import gg.modl.backend.ticket.data.TicketReply;
import gg.modl.backend.ticket.data.TicketStatus;
import gg.modl.backend.ticket.dto.request.BulkTicketUpdateRequest;
import gg.modl.backend.ticket.dto.request.CreateTicketRequest;
import gg.modl.backend.ticket.dto.request.QuickResponseRequest;
import gg.modl.backend.ticket.dto.request.SubmitTicketFormRequest;
import gg.modl.backend.ticket.dto.request.UpdateTicketRequest;
import gg.modl.backend.ticket.dto.response.QuickResponseResult;
import gg.modl.backend.ticket.dto.response.TicketResponse;
import gg.modl.backend.ticket.util.TicketAssigneeUtil;
import gg.modl.backend.infrastructure.util.MongoKeyUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TicketService {
    private final TicketMongoRepository ticketRepository;
    private final StaffMongoRepository staffRepository;
    private final QuickResponseSettingsService quickResponseSettingsService;
    private final TicketFormSettingsService ticketFormSettingsService;
    private final TicketNotificationService notificationService;
    private final TicketIdGenerator ticketIdGenerator;
    private final TicketContentService contentService;
    private final WebhookSettingsService webhookSettingsService;
    private static final String AVATAR_URL_FORMAT = "https://mc-heads.net/avatar/%s/32";

    public TicketResponse getTicketById(Server server, String ticketId) {
        Ticket ticket = ticketRepository.findById(server, ticketId)
            .orElseThrow(() -> new ResourceNotFoundException("Ticket not found"));
        return toTicketResponse(server, ticket);
    }

    public TicketResponse toResponse(Server server, Ticket ticket) {
        return toTicketResponse(server, ticket);
    }

    private TicketResponse toTicketResponse(Server server, Ticket ticket) {
        List<TicketReply> processedReplies = processRepliesWithNames(server, ticket);
        String creatorName = ticket.getCreatorName() != null ? ticket.getCreatorName() : "Unknown";

        return new TicketResponse(
            ticket.getId(),
            ticket.getType() != null ? ticket.getType().getId() : TicketCategory.SUPPORT.getId(),
            ticket.getType() != null ? ticket.getType().getDisplayName() : TicketCategory.SUPPORT.getDisplayName(),
            ticket.getSubject() != null ? ticket.getSubject() : "No Subject",
            ticket.getStatus() != null ? ticket.getStatus().getId() : TicketStatus.OPEN.getId(),
            ticket.getAppealWorkflowStatus() != null ? ticket.getAppealWorkflowStatus().getId() : null,
            creatorName,
            ticket.getCreatorUuid(),
            creatorName,
            ticket.getReportedPlayer(),
            ticket.getReportedPlayerUuid(),
            ticket.getCreated(),
            ticket.isLocked(),
            processedReplies,
            ticket.getNotes(),
            ticket.getTags(),
            ticket.getFormData(),
            ticket.getData(),
            ticket.getChatMessages(),
            ticket.getAiAnalysis(),
            ticket.isEmailAuthEnabled(),
            ticket.isHidden(),
            ticket.getReplayUrl(),
            ticket.getAssignedTo()
        );
    }

    private List<TicketReply> processRepliesWithNames(Server server, Ticket ticket) {
        if (ticket.getReplies() == null || ticket.getReplies().isEmpty()) {
            return ticket.getReplies();
        }

        String creatorName = ticket.getCreatorName() != null ? ticket.getCreatorName() : "Player";

        Set<String> staffUsernames = new HashSet<>();
        for (TicketReply reply : ticket.getReplies()) {
            if (reply.isStaff() && (reply.getAvatar() == null || reply.getAvatar().isBlank()) && reply.getName() != null && !reply.getName().isBlank()) {
                staffUsernames.add(reply.getName());
            }
        }

        Map<String, String> staffAvatarMap = new HashMap<>();
        if (!staffUsernames.isEmpty()) {
            Map<String, Staff> staffByUsername = staffRepository.findByUsernames(server, staffUsernames)
                .stream()
                .collect(Collectors.toMap(Staff::getUsername, Function.identity(), (a, b) -> a));

            for (String username : staffUsernames) {
                Staff staff = staffByUsername.get(username);
                if (staff != null && staff.getAssignedMinecraftUuid() != null && !staff.getAssignedMinecraftUuid().isBlank()) {
                    staffAvatarMap.put(username, String.format(AVATAR_URL_FORMAT, staff.getAssignedMinecraftUuid()));
                }
            }
        }

        return ticket.getReplies()
            .stream().map(reply -> {
                String name = reply.getName();
                if (name == null || name.isBlank()) {
                    name = reply.isStaff() ? "Staff" : creatorName;
                }
                String type = reply.getType();
                if (type == null || type.isBlank()) {
                    type = reply.isStaff() ? "staff" : "user";
                }
                String avatar = reply.getAvatar();
                if (reply.isStaff() && (avatar == null || avatar.isBlank()) && name != null) {
                    String staffAvatar = staffAvatarMap.get(name);
                    if (staffAvatar != null) {
                        avatar = staffAvatar;
                    }
                }
                return reply.toBuilder().name(name).type(type).avatar(avatar).build();
            }).toList();
    }

    public Optional<Ticket> getTicketRaw(Server server, String ticketId) {
        return ticketRepository.findById(server, ticketId);
    }

    public TicketResponse createTicket(Server server, CreateTicketRequest request) {
        return createTicketInternal(server, request, false);
    }

    public TicketResponse createUnfinishedTicket(Server server, CreateTicketRequest request) {
        return createTicketInternal(server, request, true);
    }

    private TicketResponse createTicketInternal(Server server, CreateTicketRequest request, boolean forceUnfinished) {
        TicketCategory ticketCategory = TicketCategory.fromCanonicalId(request.type());

        boolean shouldOpenImmediately = ticketCategory.isReport()
                                        || (request.subject() != null && !request.subject().isBlank());
        TicketStatus ticketStatus = forceUnfinished
                                    ? TicketStatus.UNFINISHED
                                    : (shouldOpenImmediately ? TicketStatus.OPEN : TicketStatus.UNFINISHED);
        String subject = (request.subject() != null && !request.subject().isBlank())
                         ? request.subject()
                         : ticketCategory.getDisplayName();

        List<String> tags = request.tags() != null ? new ArrayList<>(request.tags()) : new ArrayList<>();

        Map<String, Object> data = new HashMap<>();
        String creatorEmail = EmailAddressUtil.normalizeIfValid(request.creatorEmail());
        if (creatorEmail != null) {
            data.put("creatorEmail", creatorEmail);
        }
        if (request.creatorIdentifier() != null) {
            data.put("creatorIdentifier", request.creatorIdentifier());
        }

        TicketContentService.FormDataProcessingResult createFormDataProcessing = contentService.processFormDataForContent(request.formData());
        List<Object> initialAttachments = contentService.mergeAttachments(
            contentService.normalizeAttachments(request.attachments()),
            createFormDataProcessing.attachments()
        );

        List<TicketReply> replies = new ArrayList<>();
        String content = contentService.buildTicketContent(request, createFormDataProcessing.formData());
        if (!content.isBlank() || !initialAttachments.isEmpty()) {
            TicketReply initialReply = TicketReply.builder()
                .id(UUID.randomUUID().toString())
                .name(request.creatorName() != null ? request.creatorName() : "API User")
                .content(content)
                .type("user")
                .created(new Date())
                .staff(false)
                .attachments(initialAttachments)
                .creatorIdentifier(request.creatorIdentifier())
                .build();
            replies.add(initialReply);
        }

        String creatorDisplayName = request.creatorName() != null ? request.creatorName() : "API User";

        boolean emailAuth = Boolean.TRUE.equals(request.emailAuthEnabled());

        TicketFormSettings.TicketForm formSettings = ticketFormSettingsService.getFormByType(server, ticketCategory.getId());
        if (formSettings != null && formSettings.isRequireEmailAuth()) {
            emailAuth = true;
        }

        Ticket ticket = Ticket.builder()
            .type(ticketCategory)
            .subject(subject)
            .status(ticketStatus)
            .appealWorkflowStatus(ticketCategory.isAppeal() ? AppealWorkflowStatus.OPEN : null)
            .creatorName(creatorDisplayName)
            .creatorUuid(normalizeUuid(request.creatorUuid()))
            .reportedPlayer(request.reportedPlayerName())
            .reportedPlayerUuid(normalizeUuid(request.reportedPlayerUuid()))
            .tags(tags)
            .replies(replies)
            .notes(new ArrayList<>())
            .chatMessages(request.chatMessages() == null || request.chatMessages().isEmpty() ? null : contentService.sanitizeChatMessages(request.chatMessages()))
            .formData(MongoKeyUtils.sanitizeKeys(request.formData()))
            .data(data)
            .locked(ticketStatus.isTerminal())
            .priority(TicketPriority.resolveOrDefault(request.priority()))
            .emailAuthEnabled(emailAuth)
            .created(new Date())
            .updatedAt(new Date())
            .build();

        Ticket saved = ticketIdGenerator.insertWithUniqueId(server, ticketCategory.getTicketPrefix(), ticket);

        if (!forceUnfinished) {
            webhookSettingsService.sendTicketCreatedWebhook(server, Map.of(
                "id", saved.getId(),
                "type", ticketCategory.getDisplayName(),
                "title", subject,
                "priority", saved.getPriority() != null ? saved.getPriority().name() : "Normal",
                "category", ticketCategory.getDisplayName(),
                "submittedBy", creatorDisplayName
            ));
        }

        return toTicketResponse(server, saved);
    }

    public TicketResponse updateTicket(Server server, String ticketId, UpdateTicketRequest request, String staffEmail) {
        Ticket ticket = ticketRepository.findById(server, ticketId)
            .orElseThrow(() -> new ResourceNotFoundException("Ticket not found"));
        boolean wasClosed = ticket.getStatus() != null && ticket.getStatus().isTerminal();
        ticket.setUpdatedAt(new Date());

        if (request.status() != null) {
            ticket.applyLifecycleStatus(TicketStatus.fromCanonicalId(request.status()));
        }

        if (request.locked() != null) {
            ticket.setLocked(request.locked());
        }

        if (request.tags() != null) {
            ticket.setTags(request.tags());
        }

        if (request.hidden() != null) {
            ticket.setHidden(request.hidden());
        }

        if (request.assignedTo() != null) {
            ticket.setAssignedTo(TicketAssigneeUtil.normalizeCollection(request.assignedTo()));
        }

        if (request.data() != null && !request.data().isEmpty()) {
            Map<String, Object> existingData = ticket.getData() != null ? new HashMap<>(ticket.getData()) : new HashMap<>();
            existingData.putAll(MongoKeyUtils.sanitizeKeys(request.data()));
            ticket.setData(existingData);
        }

        TicketReply newReply = null;
        if (request.newReply() != null) {
            newReply = TicketReply.builder()
                .id(UUID.randomUUID().toString())
                .name(request.newReply().name())
                .avatar(request.newReply().avatar())
                .content(request.newReply().content())
                .type(request.newReply().type() != null ? request.newReply().type() : "public")
                .created(new Date())
                .staff(request.newReply().staff())
                .action(request.newReply().action())
                .attachments(request.newReply().attachments() != null ? request.newReply().attachments() : new ArrayList<>())
                .creatorIdentifier(request.newReply().creatorIdentifier())
                .build();

            ticket.ensureReplies().add(newReply);
        }

        if (request.newNote() != null) {
            TicketNote newNote = TicketNote.builder()
                .text(request.newNote().text())
                .issuerName(request.newNote().issuerName())
                .issuerAvatar(request.newNote().issuerAvatar())
                .date(new Date())
                .build();

            ticket.ensureNotes().add(newNote);
        }

        Ticket saved = ticketRepository.saveEntity(server, ticket);

        if (newReply != null && newReply.isStaff()) {
            notificationService.notifyTicketReply(server, saved, newReply);
        }

        if (!wasClosed && saved.getStatus() != null && saved.getStatus().isTerminal()) {
            notificationService.notifyTicketClosed(server, saved);
        }

        return toTicketResponse(server, saved);
    }

    public int bulkUpdateTickets(Server server, BulkTicketUpdateRequest request, String staffEmail) {
        List<Ticket> tickets = ticketRepository.findByIds(server, request.ticketIds());
        int updatedCount = 0;

        for (Ticket ticket : tickets) {
            Date now = new Date();
            ticket.setUpdatedAt(now);
            boolean hasChanges = false;
            boolean wasClosed = ticket.getStatus() != null && ticket.getStatus().isTerminal();

            if (request.locked() != null) {
                TicketStatus nextStatus = request.locked()
                                          ? TicketStatus.CLOSED
                                          : (ticket.getStatus() == TicketStatus.UNFINISHED ? TicketStatus.UNFINISHED : TicketStatus.OPEN);
                ticket.applyLifecycleStatus(nextStatus);
                hasChanges = true;
            }

            if (request.hidden() != null) {
                ticket.setHidden(request.hidden());
                hasChanges = true;
            }

            if (request.addLabels() != null && !request.addLabels().isEmpty()) {
                List<String> currentTags = ticket.getTags() != null ? new ArrayList<>(ticket.getTags()) : new ArrayList<>();
                for (String label : request.addLabels()) {
                    if (!currentTags.contains(label)) {
                        currentTags.add(label);
                    }
                }
                ticket.setTags(currentTags);
                hasChanges = true;
            }

            if (request.removeLabels() != null && !request.removeLabels().isEmpty()) {
                List<String> currentTags = ticket.getTags() != null ? new ArrayList<>(ticket.getTags()) : new ArrayList<>();
                currentTags.removeAll(request.removeLabels());
                ticket.setTags(currentTags);
                hasChanges = true;
            }

            if (request.assignTo() != null) {
                List<String> assignees = "none".equalsIgnoreCase(request.assignTo())
                                         ? List.of()
                                         : TicketAssigneeUtil.normalizeCsv(request.assignTo());
                ticket.setAssignedTo(assignees);
                hasChanges = true;
            }

            if (hasChanges) {
                Ticket saved = ticketRepository.saveEntity(server, ticket);
                updatedCount++;

                if (!wasClosed && saved.getStatus() != null && saved.getStatus().isTerminal()) {
                    notificationService.notifyTicketClosed(server, saved);
                }
            }
        }

        return updatedCount;
    }

    public QuickResponseResult processQuickResponse(Server server, String ticketId, QuickResponseRequest request, String staffUsername) {
        Ticket ticket = ticketRepository.findById(server, ticketId).orElse(null);
        if (ticket == null) {
            return new QuickResponseResult(false, "Ticket not found", null, null, false, false, null);
        }

        QuickResponseSettings settings = quickResponseSettingsService.getQuickResponseSettings(server);
        if (settings == null) {
            return new QuickResponseResult(false, "Quick response settings not found", ticketId, null, false, false, null);
        }

        QuickResponseSettings.Action action = quickResponseSettingsService.findAction(settings, request.categoryId(), request.actionId());
        if (action == null) {
            return new QuickResponseResult(false, "Action not found", ticketId, null, false, false, null);
        }

        TicketReply responseReply = TicketReply.builder()
            .id(UUID.randomUUID().toString())
            .name(staffUsername != null ? staffUsername : "System")
            .content(action.getMessage())
            .type("public")
            .created(new Date())
            .staff(true)
            .build();
        ticket.ensureReplies().add(responseReply);
        ticket.setUpdatedAt(new Date());
        boolean ticketClosed = false;
        if (Boolean.TRUE.equals(action.getCloseTicket())) {
            ticket.applyLifecycleStatus(TicketStatus.CLOSED);
            ticketClosed = true;
        }

        Ticket saved = ticketRepository.saveEntity(server, ticket);

        notificationService.notifyTicketReply(server, saved, responseReply);

        if (ticketClosed) {
            notificationService.notifyTicketClosed(server, saved);
        }

        return new QuickResponseResult(
            true,
            "Quick response applied successfully",
            ticketId,
            action.getName(),
            ticketClosed,
            false,
            action.getAppealAction()
        );
    }

    public TicketResponse submitTicketForm(Server server, String ticketId, SubmitTicketFormRequest request, boolean emailVerified) {
        Ticket ticket = ticketRepository.findById(server, ticketId)
            .orElseThrow(() -> new ResourceNotFoundException("Ticket not found"));
        if (ticket.isLocked() || (ticket.getStatus() != null && ticket.getStatus().isTerminal())) {
            throw new ConflictException("Ticket is locked and cannot be resubmitted");
        }
        Object existingCreatorEmail = ticket.getData() != null ? ticket.getData().get("creatorEmail") : null;
        if (ticket.getStatus() != TicketStatus.OPEN) {
            ticket.applyLifecycleStatus(TicketStatus.OPEN);
        }
        ticket.setUpdatedAt(new Date());

        Map<String, Object> requestFormData = request.formData() != null ? request.formData() : Collections.emptyMap();
        TicketContentService.FormDataProcessingResult formDataProcessing = contentService.processFormDataForContent(requestFormData);
        List<Object> initialAttachments = contentService.mergeAttachments(
            contentService.normalizeAttachments(request.attachments()),
            formDataProcessing.attachments()
        );

        if (request.subject() != null) {
            ticket.setSubject(request.subject());
        }

        Map<String, Object> existingData = ticket.getData() != null ? new HashMap<>(ticket.getData()) : new HashMap<>();
        boolean hasDataUpdates = false;

        if (request.formData() != null && !request.formData().isEmpty()) {
            Map<String, Object> sanitizedFormData = new HashMap<>(MongoKeyUtils.sanitizeKeys(request.formData()));

            existingData.putAll(sanitizedFormData);
            existingData.remove("creatorEmail");
            existingData.remove("creatorIdentifier");
            hasDataUpdates = true;

            Object emailAuthValue = request.formData().get("emailAuthEnabled");
            if (emailAuthValue != null && Boolean.parseBoolean(emailAuthValue.toString())) {
                ticket.setEmailAuthEnabled(true);
                existingData.put("emailAuthEnabled", true);
            }

            if (ticket.getType() != null) {
                TicketFormSettings.TicketForm formTypeSettings = ticketFormSettingsService.getFormByType(server, ticket.getType().getId());
                if (formTypeSettings != null && formTypeSettings.isRequireEmailAuth()) {
                    ticket.setEmailAuthEnabled(true);
                    existingData.put("emailAuthEnabled", true);
                }
            }

            Map<String, Object> mergedFormData = ticket.getFormData() != null
                                                 ? new HashMap<>(ticket.getFormData())
                                                 : new HashMap<>();
            mergedFormData.putAll(sanitizedFormData);
            ticket.setFormData(mergedFormData);
        }

        String creatorEmail = contentService.resolveCreatorEmail(request);
        if (creatorEmail != null) {
            boolean hasExistingEmail = existingCreatorEmail != null;
            if (!ticket.isEmailAuthEnabled() || !hasExistingEmail || emailVerified) {
                existingData.put("creatorEmail", creatorEmail);
            } else {
                existingData.put("creatorEmail", existingCreatorEmail);
            }
            hasDataUpdates = true;
        } else if (existingCreatorEmail != null) {
            existingData.put("creatorEmail", existingCreatorEmail);
            hasDataUpdates = true;
        }

        if (request.creatorIdentifier() != null) {
            existingData.put("creatorIdentifier", request.creatorIdentifier());
            hasDataUpdates = true;
        }

        if (ticket.isEmailAuthEnabled()) {
            existingData.put("emailAuthEnabled", true);
            hasDataUpdates = true;
        }

        if (hasDataUpdates) {
            ticket.setData(existingData);
        }

        if (ticket.getReplies() == null || ticket.getReplies().isEmpty()) {
            String content = contentService.buildFormDataContent(formDataProcessing.formData(), request.fieldLabels());
            if (!content.isBlank() || !initialAttachments.isEmpty()) {
                TicketReply initialReply = TicketReply.builder()
                    .id(UUID.randomUUID().toString())
                    .name(ticket.getCreatorName() != null ? ticket.getCreatorName() : "Player")
                    .content(content)
                    .type("user")
                    .created(new Date())
                    .staff(false)
                    .attachments(initialAttachments)
                    .creatorIdentifier(request.creatorIdentifier())
                    .build();
                ticket.ensureReplies().add(initialReply);
            }
        }

        Ticket saved = ticketRepository.saveEntity(server, ticket);

        return toTicketResponse(server, saved);
    }

    public void closeTicketForPunishment(Server server, String ticketId, String issuerName) {
        Ticket ticket = ticketRepository.findById(server, ticketId).orElse(null);
        if (ticket == null || ticket.isLocked()) {
            return;
        }

        TicketReply systemReply = TicketReply.builder()
            .id(UUID.randomUUID().toString())
            .name(issuerName)
            .content("Report accepted - punishment has been issued.")
            .type("public")
            .created(new Date())
            .staff(true)
            .action("report_accepted")
            .attachments(new ArrayList<>())
            .build();

        ticket.ensureReplies().add(systemReply);
        ticket.applyLifecycleStatus(TicketStatus.CLOSED);
        ticket.setUpdatedAt(new Date());
        ticketRepository.saveEntity(server, ticket);
    }

    public void reopenTicketForPunishment(Server server, String ticketId, String issuerName) {
        Ticket ticket = ticketRepository.findById(server, ticketId).orElse(null);
        if (ticket == null) {
            return;
        }

        ticket.applyLifecycleStatus(TicketStatus.OPEN);
        ticket.setUpdatedAt(new Date());
        ticketRepository.saveEntity(server, ticket);
    }

    public String getEmailHint(Ticket ticket) {
        String email = TicketEmailVerificationService.resolveContactEmail(ticket);
        return email == null ? null : EmailAddressUtil.mask(email);
    }

    public Set<String> getPublicFormFieldIds(Server server, Ticket ticket) {
        if (ticket == null || ticket.getType() == null) {
            return Set.of();
        }
        TicketFormSettings.TicketForm form = ticketFormSettingsService.getFormByType(server, ticket.getType().getId());
        if (form == null || form.getFields() == null) {
            return Set.of();
        }
        return form.getFields().stream()
            .map(TicketFormSettings.FormField::getId)
            .filter(id -> id != null && !id.isBlank())
            .collect(Collectors.toSet());
    }

    private static String normalizeUuid(String value) {
        return value == null ? null : value.toLowerCase(java.util.Locale.ROOT);
    }

}
