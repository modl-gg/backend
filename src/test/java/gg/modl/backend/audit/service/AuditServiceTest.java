package gg.modl.backend.audit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import gg.modl.backend.audit.data.AuditLog;
import gg.modl.backend.database.mongo.repository.AuditLogRepository;
import gg.modl.backend.database.mongo.repository.PunishmentMongoRepository;
import gg.modl.backend.database.mongo.repository.StaffMongoRepository;
import gg.modl.backend.player.data.punishment.Punishment;
import gg.modl.backend.player.service.PlayerStatusCalculator;
import gg.modl.backend.player.service.PunishmentLifecycleService;
import gg.modl.backend.player.service.PunishmentMutationService;
import gg.modl.backend.player.service.PunishmentQueryService;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.settings.service.PunishmentTypeService;
import gg.modl.backend.staff.service.StaffService;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private PunishmentMongoRepository punishmentRepository;

    @Mock
    private StaffMongoRepository staffMongoRepository;

    @Mock
    private PunishmentTypeService punishmentTypeService;

    @Mock
    private StaffService staffService;

    @Mock
    private PlayerStatusCalculator statusCalculator;

    @Mock
    private PunishmentLifecycleService punishmentLifecycleService;

    @Mock
    private PunishmentMutationService punishmentMutationService;

    @Mock
    private Server server;

    private AuditService auditService;

    @BeforeEach
    void setUp() {
        auditService = new AuditService(
            auditLogRepository,
            punishmentRepository,
            staffMongoRepository,
            punishmentTypeService,
            staffService,
            statusCalculator,
            punishmentLifecycleService,
            punishmentMutationService
        );
    }

    @Test
    void bulkPardonByType_preserves_uuid_shaped_player_ids_for_mutation_and_audit_metadata() {
        String playerId = UUID.randomUUID().toString();
        String punishmentId = "ABCDWXYZ";
        int typeOrdinal = 5;

        // Embedded punishment subdocs store the short id under the production field name "id" only.
        Document punishmentDoc = new Document("id", punishmentId)
            .append("typeOrdinal", typeOrdinal)
            .append("issuerName", "StaffMember")
            .append("issued", new Date())
            .append("data", new Document("status", "Active"));

        Document playerDoc = new Document("_id", playerId)
            .append("usernames", List.of(new Document("username", "TargetPlayer")))
            .append("punishments", List.of(punishmentDoc));

        when(server.getId()).thenReturn("server-1");
        when(punishmentRepository.findPlayersForBulkAction(server, List.of(typeOrdinal)))
            .thenReturn(List.of(playerDoc));
        when(punishmentTypeService.getPunishmentTypeName(server, typeOrdinal)).thenReturn("Ban");
        when(statusCalculator.isPunishmentActive(org.mockito.ArgumentMatchers.any(Punishment.class))).thenReturn(true);
        when(punishmentLifecycleService.pardonPunishment(
                eq(server), eq(punishmentId), eq("Moderator"), isNull(), eq("Cleanup reason")))
            .thenReturn(new PunishmentQueryService.PunishmentOperationResult(
                PunishmentQueryService.PunishmentOperationStatus.SUCCESS, "", true, 1));

        int affected = auditService.bulkPardonByType(
            server,
            List.of(typeOrdinal),
            "Cleanup reason",
            "Moderator"
        );

        assertEquals(1, affected);
        // Bulk pardon now delegates to the canonical pardon entry point (non-null punishment id required).
        verify(punishmentLifecycleService).pardonPunishment(
            eq(server), eq(punishmentId), eq("Moderator"), isNull(), eq("Cleanup reason"));

        ArgumentCaptor<AuditLog> auditLogCaptor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).saveAuditLog(eq(server), auditLogCaptor.capture());
        assertEquals(playerId, auditLogCaptor.getValue().getMetadata().get("playerId"));
        assertEquals(punishmentId, auditLogCaptor.getValue().getMetadata().get("punishmentId"));
    }
}
