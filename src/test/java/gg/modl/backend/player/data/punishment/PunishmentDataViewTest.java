package gg.modl.backend.player.data.punishment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PunishmentDataViewTest {

    @Test
    void mutatingOneFieldPreservesUnknownAndUntouchedKeys() {
        Map<String, Object> stored = new HashMap<>();
        stored.put(PunishmentData.STATUS, "Active");
        stored.put(PunishmentData.DURATION, 5000L);
        stored.put(PunishmentData.ALT_BLOCKING, true);
        stored.put("legacyPluginFlag", "keep-me");

        PunishmentDataView view = PunishmentDataView.ofMap(stored);
        view.setStatus(PunishmentStatus.PARDONED);

        assertEquals(PunishmentStatus.PARDONED, stored.get(PunishmentData.STATUS));
        assertEquals(5000L, stored.get(PunishmentData.DURATION));
        assertEquals(true, stored.get(PunishmentData.ALT_BLOCKING));
        assertEquals("keep-me", stored.get("legacyPluginFlag"));
        assertEquals(4, stored.size());
    }

    @Test
    void readsAreNullSafeAndDoNotMaterialiseMapForOwner() {
        Punishment punishment = new Punishment();
        punishment.replaceData(null);

        assertNull(punishment.data().status());
        assertNull(punishment.data().duration());
        assertFalse(punishment.data().altBlocking());
        assertFalse(punishment.data().wipeAfterExpiry());
        assertFalse(punishment.data().hasStatus());
        assertNull(punishment.data().asMap());
    }

    @Test
    void mutatorLazilyInstallsMapOnOwnerWhenAbsent() {
        Punishment punishment = new Punishment();
        punishment.replaceData(null);

        punishment.data().setStatus(PunishmentStatus.UNSTARTED);

        assertEquals(PunishmentStatus.UNSTARTED, punishment.data().status());
        assertSame(punishment.data().asMap(), punishment.data().asMap());
        assertEquals(1, punishment.data().asMap().size());
    }

    @Test
    void wrongTypedLegacyValuesAreToleratedLikeTheOldAccessor() {
        Map<String, Object> stored = new HashMap<>();
        stored.put(PunishmentData.DURATION, "not-a-number");
        stored.put(PunishmentData.ALT_BLOCKING, "true");
        stored.put(PunishmentData.STATUS, 42);

        PunishmentDataView view = PunishmentDataView.ofMap(stored);

        assertNull(view.duration());
        assertFalse(view.altBlocking());
        assertNull(view.status());
    }

    @Test
    void durationAcceptsIntegerBackedLegacyValues() {
        Map<String, Object> stored = new HashMap<>();
        stored.put(PunishmentData.DURATION, 7);

        assertEquals(7L, PunishmentDataView.ofMap(stored).duration());
    }

    @Test
    void removePendingAcknowledgementReturnsAndClearsFlag() {
        Map<String, Object> stored = new HashMap<>();
        stored.put(PunishmentData.PENDING_ACKNOWLEDGEMENT, true);

        PunishmentDataView view = PunishmentDataView.ofMap(stored);

        assertTrue(view.removePendingAcknowledgement());
        assertFalse(stored.containsKey(PunishmentData.PENDING_ACKNOWLEDGEMENT));
        assertFalse(view.removePendingAcknowledgement());
    }

    @Test
    void toggleOptionAppliesTypedMutationAndExposesPersistenceKey() {
        Map<String, Object> stored = new HashMap<>();
        PunishmentDataView view = PunishmentDataView.ofMap(stored);

        PunishmentToggleOption.STAT_WIPE.apply(view, true);

        assertTrue(view.wipeAfterExpiry());
        assertEquals(PunishmentData.WIPE_AFTER_EXPIRY, PunishmentToggleOption.STAT_WIPE.dataKey());
        assertEquals(PunishmentToggleOption.ALT_BLOCKING, PunishmentToggleOption.from("alt_blocking"));
        assertNull(PunishmentToggleOption.from("nonsense"));
    }
}
