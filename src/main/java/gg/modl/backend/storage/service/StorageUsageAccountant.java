package gg.modl.backend.storage.service;

import gg.modl.backend.database.mongo.repository.ServerUsageRepository;
import gg.modl.backend.server.data.Server;
import java.util.OptionalLong;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StorageUsageAccountant {
    private final ServerUsageRepository serverUsageRepository;

    public OptionalLong trackedBytes(Server server) {
        Long tracked = server.getStorageUsedBytes();
        return tracked != null ? OptionalLong.of(tracked) : OptionalLong.empty();
    }

    public boolean isSynced(Server server) {
        return trackedBytes(server).isPresent();
    }

    public void recordAddition(Server server, long bytes) {
        serverUsageRepository.incrementStorageUsed(server.getId(), bytes);
    }

    public boolean tryReserveWithinLimit(Server server, long bytes, long maxBytes) {
        return serverUsageRepository.tryIncrementStorageUsedWithinLimit(server.getId(), bytes, maxBytes);
    }

    public void recordRemoval(Server server, long bytes) {
        serverUsageRepository.decrementStorageUsed(server.getId(), bytes);
    }

    public void setAuthoritativeUsage(Server server, long totalBytes) {
        serverUsageRepository.setStorageUsed(server.getId(), totalBytes);
    }

    public boolean lowerUsageToActual(Server server, long totalBytes) {
        return serverUsageRepository.setStorageUsedIfBelow(server.getId(), totalBytes);
    }
}
