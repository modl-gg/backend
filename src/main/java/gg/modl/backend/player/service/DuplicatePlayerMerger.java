package gg.modl.backend.player.service;

import gg.modl.backend.player.data.IPEntry;
import gg.modl.backend.player.data.NoteEntry;
import gg.modl.backend.player.data.Player;
import gg.modl.backend.player.data.UsernameEntry;
import gg.modl.backend.player.data.punishment.Punishment;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.springframework.stereotype.Component;

@Component
public class DuplicatePlayerMerger {
    private static final Set<String> UNION_LIST_DATA_KEYS = Set.of("linkedAccounts", "pendingNotifications");

    private static final Comparator<Player> RICHNESS = Comparator
        .comparingInt((Player player) -> size(player.getPunishments()) + size(player.getNotes()))
        .thenComparingInt(player -> size(player.getIpAddresses()))
        .thenComparingInt(player -> size(player.getUsernames()))
        .thenComparing(Player::getId, Comparator.nullsFirst(Comparator.naturalOrder()));

    public Player merge(List<Player> duplicates) {
        Player primary = duplicates.stream().max(RICHNESS).orElseThrow();
        for (Player other : duplicates) {
            if (other == primary) {
                continue;
            }
            mergeInto(primary, other);
        }
        return primary;
    }

    private void mergeInto(Player primary, Player other) {
        primary.setUsernames(mergeBy(primary.getUsernames(), other.getUsernames(), UsernameEntry::username));
        primary.setIpAddresses(mergeBy(primary.getIpAddresses(), other.getIpAddresses(), IPEntry::getIpAddress));
        primary.setNotes(mergeBy(primary.getNotes(), other.getNotes(), NoteEntry::getId));
        primary.setPunishments(mergeBy(primary.getPunishments(), other.getPunishments(), Punishment::getId));
        primary.setData(mergeData(primary.getData(), other.getData()));
    }

    private <T> List<T> mergeBy(List<T> primary, List<T> other, Function<T, Object> identity) {
        List<T> merged = new ArrayList<>(nullSafe(primary));
        Set<Object> seen = new HashSet<>();
        for (T entry : merged) {
            seen.add(identityKey(entry, identity));
        }
        for (T entry : nullSafe(other)) {
            if (seen.add(identityKey(entry, identity))) {
                merged.add(entry);
            }
        }
        return merged;
    }

    private <T> Object identityKey(T entry, Function<T, Object> identity) {
        Object id = identity.apply(entry);
        return id != null ? id : entry;
    }

    private Map<String, Object> mergeData(Map<String, Object> primary, Map<String, Object> other) {
        Map<String, Object> merged = new HashMap<>(nullSafe(primary));
        for (Map.Entry<String, Object> entry : nullSafe(other).entrySet()) {
            if (UNION_LIST_DATA_KEYS.contains(entry.getKey())) {
                merged.put(entry.getKey(), unionLists(merged.get(entry.getKey()), entry.getValue()));
            } else {
                merged.putIfAbsent(entry.getKey(), entry.getValue());
            }
        }
        return merged;
    }

    private List<Object> unionLists(Object primary, Object other) {
        Set<Object> merged = new LinkedHashSet<>();
        collectListItems(merged, primary);
        collectListItems(merged, other);
        return new ArrayList<>(merged);
    }

    private void collectListItems(Set<Object> target, Object value) {
        if (value instanceof List<?> list) {
            target.addAll(list);
        } else if (value != null) {
            target.add(value);
        }
    }

    private static int size(List<?> list) {
        return list == null ? 0 : list.size();
    }

    private static <T> List<T> nullSafe(List<T> list) {
        return list == null ? List.of() : list;
    }

    private static <K, V> Map<K, V> nullSafe(Map<K, V> map) {
        return map == null ? Map.of() : map;
    }
}
