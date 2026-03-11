package gg.modl.backend.server.service;

import gg.modl.backend.database.mongo.repository.ServerMongoRepository;
import gg.modl.backend.server.data.Server;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;

import java.util.Date;

@Service
@RequiredArgsConstructor
public class ServerTimestampService {
    private final ServerMongoRepository serverRepository;

    public void updateStaffPermissionsTimestamp(@NotNull Server server) {
        serverRepository.updateStaffPermissionsTimestamp(server.getId(), new Date());
    }

    public void updatePunishmentTypesTimestamp(@NotNull Server server) {
        serverRepository.updatePunishmentTypesTimestamp(server.getId(), new Date());
    }
}
