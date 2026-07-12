package gg.modl.backend.player.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gg.modl.backend.player.service.MojangApiService.MojangProfile;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MojangApiServiceTest {

    @Test
    void parsesDashlessProfileByName() {
        String json = "{\"id\":\"853c80ef3c3749fdaa49938b674adae6\",\"name\":\"jeb_\"}";

        Optional<MojangProfile> profile = MojangApiService.parseProfile(json);

        assertTrue(profile.isPresent());
        assertEquals("jeb_", profile.get().name());
        assertEquals(UUID.fromString("853c80ef-3c37-49fd-aa49-938b674adae6"), profile.get().uuid());
    }

    @Test
    void readsTopLevelNameNotNestedPropertyName() {
        String json = "{\"id\":\"853c80ef3c3749fdaa49938b674adae6\",\"name\":\"Notch\","
            + "\"properties\":[{\"name\":\"textures\",\"value\":\"eyJ0ZXh0dXJlcyI6e319\"}]}";

        Optional<MojangProfile> profile = MojangApiService.parseProfile(json);

        assertTrue(profile.isPresent());
        assertEquals("Notch", profile.get().name());
        assertEquals(UUID.fromString("853c80ef-3c37-49fd-aa49-938b674adae6"), profile.get().uuid());
    }

    @Test
    void decodesUnicodeEscapedName() {
        String json = "{\"id\":\"853c80ef3c3749fdaa49938b674adae6\",\"name\":\"a\\u0062c\"}";

        Optional<MojangProfile> profile = MojangApiService.parseProfile(json);

        assertTrue(profile.isPresent());
        assertEquals("abc", profile.get().name());
    }

    @Test
    void keepsAlreadyDashedUuid() {
        String json = "{\"id\":\"853c80ef-3c37-49fd-aa49-938b674adae6\",\"name\":\"jeb_\"}";

        Optional<MojangProfile> profile = MojangApiService.parseProfile(json);

        assertTrue(profile.isPresent());
        assertEquals(UUID.fromString("853c80ef-3c37-49fd-aa49-938b674adae6"), profile.get().uuid());
    }

    @Test
    void returnsEmptyWhenNameMissing() {
        String json = "{\"id\":\"853c80ef3c3749fdaa49938b674adae6\"}";

        assertFalse(MojangApiService.parseProfile(json).isPresent());
    }

    @Test
    void returnsEmptyWhenIdMissing() {
        String json = "{\"name\":\"jeb_\"}";

        assertFalse(MojangApiService.parseProfile(json).isPresent());
    }

    @Test
    void returnsEmptyForMalformedJson() {
        assertFalse(MojangApiService.parseProfile("not json").isPresent());
    }

    @Test
    void returnsEmptyForEmptyBody() {
        assertFalse(MojangApiService.parseProfile("").isPresent());
    }
}
