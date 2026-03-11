package gg.modl.backend.util;

import gg.modl.backend.database.mongo.repository.ServerMongoRepository;
import gg.modl.backend.server.data.Server;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.function.Consumer;

@Component
@RequiredArgsConstructor
public class ServerMutationHelper {

    private final ServerMongoRepository serverRepository;

    public void mutate(Server server, Consumer<Server> mutator) {
        mutator.accept(server);
        serverRepository.saveEntity(server);
    }
}
