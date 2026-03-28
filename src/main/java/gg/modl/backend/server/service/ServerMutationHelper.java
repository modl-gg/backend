package gg.modl.backend.server.service;

import gg.modl.backend.database.mongo.repository.ServerMongoRepository;
import gg.modl.backend.server.ServerService;
import gg.modl.backend.server.data.Server;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ServerMutationHelper {

    private final ServerMongoRepository serverRepository;
    private final ServerService serverService;

    public void mutate(Server server, Consumer<Server> mutator) {
        mutator.accept(server);
        serverRepository.saveEntity(server);
        serverService.evictAllServerCaches();
    }
}
