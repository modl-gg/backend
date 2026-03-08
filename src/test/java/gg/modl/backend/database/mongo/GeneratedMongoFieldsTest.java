package gg.modl.backend.database.mongo;

import gg.modl.backend.database.mongo.fields.AdminUserFields;
import gg.modl.backend.database.mongo.fields.ChatLogFields;
import gg.modl.backend.database.mongo.fields.CommandLogFields;
import gg.modl.backend.database.mongo.fields.PlayerFields;
import gg.modl.backend.database.mongo.fields.PunishmentFields;
import gg.modl.backend.database.mongo.fields.SecurityEventFields;
import gg.modl.backend.database.mongo.fields.ServerFields;
import gg.modl.backend.database.mongo.fields.SystemLogFields;
import gg.modl.backend.database.mongo.fields.TicketFields;
import gg.modl.backend.server.ServerField;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GeneratedMongoFieldsTest {

    @Test
    void generatedFieldsPreservePersistedMongoPaths() {
        assertEquals("_id", AdminUserFields.ID);
        assertEquals("serverName", ServerFields.SERVER_NAME);
        assertEquals(ServerField.CUSTOM_DOMAIN, ServerFields.CUSTOM_DOMAIN_OVERRIDE);
        assertEquals("timestamp", SystemLogFields.TIMESTAMP);
        assertEquals("uuid", ChatLogFields.UUID);
        assertEquals("command", CommandLogFields.COMMAND);
        assertEquals("severity", SecurityEventFields.SEVERITY);
    }

    @Test
    void generatedAliasFieldsPreserveNestedAndPositionalPaths() {
        assertEquals("usernames.username", PlayerFields.USERNAME);
        assertEquals("notes.id", PlayerFields.NOTE_ID);
        assertEquals("ipAddresses.$.country", PlayerFields.IP_COUNTRY);
        assertEquals("data.lastLinkedUpdate", PlayerFields.DATA_LAST_LINKED_UPDATE);
        assertEquals("punishments.$.data", PlayerFields.PUNISHMENT_DATA);
        assertEquals("data.linkedBanId", PunishmentFields.DATA_LINKED_BAN_ID);
        assertEquals("replies.name", TicketFields.REPLY_NAME);
        assertEquals("replies.created", TicketFields.REPLY_CREATED);
    }
}
