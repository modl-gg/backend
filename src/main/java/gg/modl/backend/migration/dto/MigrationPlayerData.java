package gg.modl.backend.migration.dto;

import java.util.List;
import java.util.Map;

public record MigrationPlayerData(
        String minecraftUuid,
        List<UsernameData> usernames,
        List<NoteData> notes,
        List<IPData> ipList,
        List<PunishmentData> punishments,
        Map<String, Object> data
) {
    public record UsernameData(String username, String date) {}

    public record NoteData(String text, String date, String issuerName) {}

    public record IPData(
            String ipAddress,
            String country,
            String region,
            String asn,
            Boolean proxy,
            Boolean hosting,
            String firstLogin,
            List<String> logins
    ) {}

    public record PunishmentData(
            String _id,
            String type,
            int type_ordinal,
            String reason,
            String issued,
            String issuerName,
            Long duration,
            String started,
            List<PunishmentNoteData> notes,
            List<Object> evidence,
            List<String> attachedTicketIds,
            List<Object> modifications,
            Map<String, Object> data
    ) {}

    public record PunishmentNoteData(String text, String issuerName, String date) {}
}
