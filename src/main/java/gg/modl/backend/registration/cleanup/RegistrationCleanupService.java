package gg.modl.backend.registration.cleanup;

import gg.modl.backend.database.mongo.repository.ServerDatabaseMongoRepository;
import gg.modl.backend.database.mongo.repository.ServerDatabaseMongoRepository.PlayerCollectionInspection;
import gg.modl.backend.database.mongo.repository.ServerRegistrationCleanupRepository;
import gg.modl.backend.registration.config.RegistrationCleanupProperties;
import gg.modl.backend.role.service.PermissionService;
import gg.modl.backend.server.ServerService;
import gg.modl.backend.server.data.ProvisioningStatus;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.staff.service.StaffLookupCache;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Slf4j
public class RegistrationCleanupService {
    private final RegistrationCleanupProperties properties;
    private final ServerRegistrationCleanupRepository serverRegistrationCleanupRepository;
    private final ServerDatabaseMongoRepository serverDatabaseRepository;
    private final ServerService serverService;
    private final PermissionService permissionService;
    private final StaffLookupCache staffLookupCache;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${modl.registration.cleanup.interval-ms:3600000}")
    public void runScheduledCleanup() {
        runCleanupOnce();
    }

    public void runCleanupOnce() {
        if (!properties.isEnabled()) {
            return;
        }

        Instant now = clock.instant();
        Date cutoff = Date.from(now.minus(properties.getExpiry()));
        Date claimCutoff = Date.from(now.minus(properties.getClaimTtl()));
        List<Server> candidates = serverRegistrationCleanupRepository.findExpiredRegistrationCleanupCandidates(
            cutoff,
            claimCutoff,
            properties.getBatchSize()
        );

        CleanupStats stats = new CleanupStats();
        for (Server candidate : candidates) {
            stats.candidates++;
            processCandidate(candidate, cutoff, claimCutoff, now, stats);
        }

        log.info(
            "Registration cleanup scanned={} deleted={} dryRun={} skippedIneligible={} skippedPlayers={} skippedUnknown={} skippedRace={} dropFailed={} finalDeleteFailed={}",
            stats.candidates,
            stats.deleted,
            properties.isDryRun(),
            stats.skippedIneligible,
            stats.skippedPlayers,
            stats.skippedUnknown,
            stats.skippedRace,
            stats.dropFailed,
            stats.finalDeleteFailed
        );
    }

    private void processCandidate(Server candidate, Date cutoff, Date claimCutoff, Instant now, CleanupStats stats) {
        if (!isStillEligible(candidate, cutoff)) {
            stats.skippedIneligible++;
            return;
        }

        PlayerCollectionInspection preClaimInspection = serverDatabaseRepository.inspectPlayersCollection(candidate);
        if (!handleInspection(preClaimInspection, stats)) {
            return;
        }

        if (properties.isDryRun()) {
            return;
        }

        Optional<Server> claimed = serverRegistrationCleanupRepository.claimExpiredRegistrationForCleanup(candidate.getId(), cutoff, claimCutoff, now);
        if (claimed.isEmpty()) {
            stats.skippedRace++;
            return;
        }

        Server claimedServer = claimed.get();
        if (!isStillEligible(claimedServer, cutoff) || !StringUtils.hasText(claimedServer.getCleanupClaimId())) {
            releaseClaimIfPresent(claimedServer);
            stats.skippedRace++;
            return;
        }

        PlayerCollectionInspection postClaimInspection = serverDatabaseRepository.inspectPlayersCollection(claimedServer);
        if (!handleInspection(postClaimInspection, stats)) {
            releaseClaimIfPresent(claimedServer);
            return;
        }

        Optional<Server> confirmed = serverRegistrationCleanupRepository.confirmRegistrationCleanupClaim(
            claimedServer.getId(),
            claimedServer.getCleanupClaimId(),
            cutoff,
            now
        );
        if (confirmed.isEmpty()) {
            stats.skippedRace++;
            return;
        }

        Server confirmedServer = confirmed.get();
        if (!serverDatabaseRepository.dropDatabase(confirmedServer)) {
            stats.dropFailed++;
            return;
        }

        boolean deleted = serverRegistrationCleanupRepository.deleteClaimedExpiredRegistration(
            confirmedServer.getId(),
            confirmedServer.getCleanupClaimId(),
            cutoff
        );
        if (!deleted) {
            stats.finalDeleteFailed++;
            return;
        }

        serverService.evictAllServerCaches();
        permissionService.evictPermissionCache();
        staffLookupCache.evictAll();
        stats.deleted++;
    }

    private void releaseClaimIfPresent(Server server) {
        if (StringUtils.hasText(server.getCleanupClaimId())) {
            serverRegistrationCleanupRepository.releaseRegistrationCleanupClaim(server.getId(), server.getCleanupClaimId());
        }
    }

    private boolean handleInspection(PlayerCollectionInspection inspection, CleanupStats stats) {
        if (inspection.status() == PlayerCollectionInspection.Status.SAFE_EMPTY) {
            return true;
        }
        if (inspection.status() == PlayerCollectionInspection.Status.BLOCKED_NON_EMPTY) {
            stats.skippedPlayers++;
            return false;
        }
        stats.skippedUnknown++;
        return false;
    }

    private boolean isStillEligible(Server server, Date cutoff) {
        return server != null
            && Boolean.FALSE.equals(server.getEmailVerified())
            && server.getProvisioningStatus() == ProvisioningStatus.PENDING
            && StringUtils.hasText(server.getEmailVerificationToken())
            && server.getCreatedAt() != null
            && server.getCreatedAt().before(cutoff)
            && StringUtils.hasText(server.getDatabaseName())
            && !hasActivityEvidence(server);
    }

    private boolean hasActivityEvidence(Server server) {
        return server.getLastActivityAt() != null
            || StringUtils.hasText(server.getApiKey())
            || positive(server.getUserCount())
            || positive(server.getTicketCount())
            || positive(server.getOnlinePlayerCount());
    }

    private boolean positive(Long value) {
        return value != null && value > 0;
    }

    private static class CleanupStats {
        private int candidates;
        private int deleted;
        private int skippedIneligible;
        private int skippedPlayers;
        private int skippedUnknown;
        private int skippedRace;
        private int dropFailed;
        private int finalDeleteFailed;
    }
}
