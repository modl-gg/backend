package gg.modl.backend.player.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gg.modl.backend.player.data.punishment.Punishment;
import gg.modl.backend.player.data.punishment.PunishmentStatus;
import java.util.Date;
import java.util.List;
import java.util.Set;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.core.convert.NoOpDbRefResolver;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;

class PlayerPersistenceRoundTripTest {

    private MappingMongoConverter converter;

    @BeforeEach
    void setUp() {
        MongoCustomConversions conversions = new MongoCustomConversions(List.of());
        MongoMappingContext context = new MongoMappingContext();
        context.setSimpleTypeHolder(conversions.getSimpleTypeHolder());
        context.setInitialEntitySet(Set.of(Player.class, Punishment.class));
        context.afterPropertiesSet();
        converter = new MappingMongoConverter(NoOpDbRefResolver.INSTANCE, context);
        converter.setCustomConversions(conversions);
        converter.afterPropertiesSet();
    }

    @Test
    void punishmentDataRoundTripPreservesUnknownKeysAndTypes() {
        Document storedData = new Document()
            .append("status", "Active")
            .append("duration", 5000L)
            .append("altBlocking", true)
            .append("offenseLevel", "habitual")
            .append("legacyPluginFlag", "keep-me")
            .append("legacyCount", 3);
        Document storedPunishment = new Document()
            .append("id", "p1")
            .append("typeOrdinal", 2)
            .append("issued", new Date(1_000L))
            .append("data", storedData);

        Punishment loaded = converter.read(Punishment.class, storedPunishment);
        assertEquals("Active", loaded.data().status());
        assertEquals("keep-me", loaded.data().asMap().get("legacyPluginFlag"));

        loaded.data().setStatus(PunishmentStatus.PARDONED);

        Document rewritten = new Document();
        converter.write(loaded, rewritten);
        Document rewrittenData = rewritten.get("data", Document.class);

        assertEquals(PunishmentStatus.PARDONED, rewrittenData.get("status"));
        assertEquals(5000L, rewrittenData.get("duration"));
        assertEquals(true, rewrittenData.get("altBlocking"));
        assertEquals("habitual", rewrittenData.get("offenseLevel"));
        assertEquals("keep-me", rewrittenData.get("legacyPluginFlag"));
        assertEquals(3, rewrittenData.get("legacyCount"));
        assertEquals(6, rewrittenData.size());
    }

    @Test
    void punishmentWithAbsentDataRoundTripsWithoutCrash() {
        Document storedPunishment = new Document()
            .append("id", "p2")
            .append("typeOrdinal", 1)
            .append("issued", new Date(2_000L));

        Punishment loaded = converter.read(Punishment.class, storedPunishment);
        assertNull(loaded.data().status());
        assertFalse(loaded.data().altBlocking());

        Document rewritten = new Document();
        converter.write(loaded, rewritten);
        assertEquals("p2", rewritten.get("id"));
    }

    @Test
    void playerDataRoundTripPreservesUnknownKeys() {
        Document storedData = new Document()
            .append("isOnline", true)
            .append("lastServer", "hub")
            .append("totalPlaytimeSeconds", 120L)
            .append("customPluginCounter", 9);
        Document storedPlayer = new Document()
            .append("_id", "player-1")
            .append("minecraftUuid", "11111111-2222-3333-4444-555555555555")
            .append("data", storedData);

        Player loaded = converter.read(Player.class, storedPlayer);
        assertEquals("hub", loaded.data().lastServer());
        assertTrue(loaded.data().isOnline());

        loaded.data().setLastServer("lobby");

        Document rewritten = new Document();
        converter.write(loaded, rewritten);
        Document rewrittenData = rewritten.get("data", Document.class);

        assertEquals("lobby", rewrittenData.get("lastServer"));
        assertEquals(true, rewrittenData.get("isOnline"));
        assertEquals(120L, rewrittenData.get("totalPlaytimeSeconds"));
        assertEquals(9, rewrittenData.get("customPluginCounter"));
        assertEquals(4, rewrittenData.size());
    }
}
