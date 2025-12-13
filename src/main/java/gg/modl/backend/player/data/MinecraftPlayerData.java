package gg.modl.backend.player.data;

import lombok.Data;

import java.util.Date;

@Data
public class MinecraftPlayerData {
    private Date firstJoin;
    private Date lastConnect;
    private String lastServer;
    private boolean isOnline;
    private long totalPlaytimeSeconds;
}
