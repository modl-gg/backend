package gg.modl.backend.player;

import gg.modl.backend.database.DynamicMongoTemplateProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PlayerService {
    private final DynamicMongoTemplateProvider mongoProvider;

    public void loginPlayer(UUID minecraftUUID, String username, String ip) {

    }
}
