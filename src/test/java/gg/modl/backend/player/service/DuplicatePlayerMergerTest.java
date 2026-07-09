package gg.modl.backend.player.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gg.modl.backend.player.data.IPEntry;
import gg.modl.backend.player.data.NoteEntry;
import gg.modl.backend.player.data.Player;
import gg.modl.backend.player.data.UsernameEntry;
import gg.modl.backend.player.data.punishment.Punishment;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class DuplicatePlayerMergerTest {
    private final DuplicatePlayerMerger merger = new DuplicatePlayerMerger();

    @Test
    void merge_unions_punishments_notes_and_alt_links_without_loss() {
        UUID uuid = UUID.randomUUID();
        Player winner = player("winner", uuid)
            .punishments(new ArrayList<>(List.of(punishment("p1"), punishment("p2"), punishment("p3"))))
            .notes(new ArrayList<>(List.of(note("n1"), note("n2"))))
            .ipAddresses(new ArrayList<>(List.of(ip("1.1.1.1"), ip("3.3.3.3"))))
            .usernames(new ArrayList<>(List.of(username("Alpha"), username("Gamma"))))
            .data(new HashMap<>(Map.of("linkedAccounts", new ArrayList<>(List.of("alt-a")))))
            .build();
        Player loser = player("loser", uuid)
            .punishments(new ArrayList<>(List.of(punishment("p3"), punishment("p4"))))
            .notes(new ArrayList<>(List.of(note("n2"), note("n3"))))
            .ipAddresses(new ArrayList<>(List.of(ip("2.2.2.2"))))
            .usernames(new ArrayList<>(List.of(username("Beta"))))
            .data(new HashMap<>(Map.of("linkedAccounts", new ArrayList<>(List.of("alt-a", "alt-b")))))
            .build();

        Player merged = merger.merge(List.of(winner, loser));

        assertEquals("winner", merged.getId());
        assertEquals(Set.of("p1", "p2", "p3", "p4"), punishmentIds(merged));
        assertEquals(Set.of("n1", "n2", "n3"), noteIds(merged));
        assertEquals(Set.of("1.1.1.1", "3.3.3.3", "2.2.2.2"), ipStrings(merged));
        assertEquals(Set.of("Alpha", "Gamma", "Beta"), usernameStrings(merged));
        assertEquals(Set.of("alt-a", "alt-b"), Set.copyOf((List<?>) merged.getData().get("linkedAccounts")));
    }

    @Test
    void merge_is_idempotent_for_entries_without_ids() {
        UUID uuid = UUID.randomUUID();
        Player primary = player("primary", uuid)
            .punishments(new ArrayList<>(List.of(punishment("p1"))))
            .notes(new ArrayList<>(List.of(anonNote())))
            .build();
        Player secondary = player("secondary", uuid)
            .punishments(new ArrayList<>(List.of(punishment("p1"))))
            .notes(new ArrayList<>(List.of(anonNote())))
            .build();

        Player firstPass = merger.merge(List.of(primary, secondary));
        Player secondPass = merger.merge(List.of(firstPass, secondary));

        assertEquals(1, secondPass.getPunishments().size());
        assertEquals(1, secondPass.getNotes().size());
        assertTrue(secondPass.getPunishments().stream().anyMatch(p -> "p1".equals(p.getId())));
    }

    private Player.PlayerBuilder player(String id, UUID uuid) {
        return Player.builder().id(id).minecraftUuid(uuid);
    }

    private Punishment punishment(String id) {
        Punishment punishment = new Punishment();
        punishment.setId(id);
        punishment.setIssued(new Date());
        return punishment;
    }

    private NoteEntry note(String id) {
        return NoteEntry.builder().id(id).text("note").date(new Date()).build();
    }

    private NoteEntry anonNote() {
        return NoteEntry.builder().id(null).text("legacy").date(new Date(0L)).build();
    }

    private IPEntry ip(String address) {
        return IPEntry.builder().ipAddress(address).firstLogin(new Date()).build();
    }

    private UsernameEntry username(String name) {
        return new UsernameEntry(name, new Date());
    }

    private Set<String> punishmentIds(Player player) {
        return player.getPunishments().stream().map(Punishment::getId).collect(Collectors.toSet());
    }

    private Set<String> noteIds(Player player) {
        return player.getNotes().stream().map(NoteEntry::getId).collect(Collectors.toSet());
    }

    private Set<String> ipStrings(Player player) {
        return player.getIpAddresses().stream().map(IPEntry::getIpAddress).collect(Collectors.toSet());
    }

    private Set<String> usernameStrings(Player player) {
        return player.getUsernames().stream().map(UsernameEntry::username).collect(Collectors.toSet());
    }
}
