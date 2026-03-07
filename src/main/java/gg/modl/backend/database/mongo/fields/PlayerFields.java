package gg.modl.backend.database.mongo.fields;

import gg.modl.backend.player.data.IPEntry;
import gg.modl.backend.player.data.NoteEntry;
import gg.modl.backend.player.data.Player;
import gg.modl.backend.player.data.UsernameEntry;
import gg.modl.backend.player.data.punishment.Punishment;
import gg.modl.backend.database.mongo.MongoField;
import gg.modl.backend.database.mongo.MongoFieldNames;

public final class PlayerFields {
    public static final MongoField<Player> ID = MongoFieldNames.field(Player.class, Player::getId);
    public static final MongoField<Player> MINECRAFT_UUID = MongoFieldNames.field(Player.class, Player::getMinecraftUuid);
    public static final MongoField<Player> USERNAMES = MongoFieldNames.field(Player.class, Player::getUsernames);
    public static final MongoField<Player> USERNAME = MongoFieldNames.field(Player.class, Player::getUsernames, UsernameEntry.class, UsernameEntry::username);
    public static final MongoField<Player> NOTES = MongoFieldNames.field(Player.class, Player::getNotes);
    public static final MongoField<Player> NOTE_ID = MongoFieldNames.field(Player.class, Player::getNotes, NoteEntry.class, NoteEntry::getId);
    public static final MongoField<Player> IP_ADDRESSES = MongoFieldNames.field(Player.class, Player::getIpAddresses);
    public static final MongoField<Player> IP_ADDRESS = MongoFieldNames.field(Player.class, Player::getIpAddresses, IPEntry.class, IPEntry::getIpAddress);
    public static final MongoField<Player> PUNISHMENTS = MongoFieldNames.field(Player.class, Player::getPunishments);
    public static final MongoField<Player> PUNISHMENT_ID = MongoFieldNames.field(Player.class, Player::getPunishments, Punishment.class, Punishment::getId);
    public static final MongoField<Player> PUNISHMENT_ISSUED = MongoFieldNames.field(Player.class, Player::getPunishments, Punishment.class, Punishment::getIssued);
    public static final MongoField<Player> PUNISHMENT_ISSUER_NAME = MongoFieldNames.field(Player.class, Player::getPunishments, Punishment.class, Punishment::getIssuerName);
    public static final MongoField<Player> DATA = MongoFieldNames.field(Player.class, Player::getData);
    public static final MongoField<Player> DATA_IS_ONLINE = MongoFieldNames.raw(Player.class, DATA.path() + ".isOnline");
    public static final MongoField<Player> DATA_TOTAL_PLAYTIME_SECONDS = MongoFieldNames.raw(Player.class, DATA.path() + ".totalPlaytimeSeconds");
    public static final MongoField<Player> DATA_LAST_SKIN_HASH = MongoFieldNames.raw(Player.class, DATA.path() + ".lastSkinHash");
    public static final MongoField<Player> DATA_FIRST_JOIN = MongoFieldNames.raw(Player.class, DATA.path() + ".firstJoin");
    public static final MongoField<Player> DATA_LAST_LOGIN = MongoFieldNames.raw(Player.class, DATA.path() + ".lastLogin");
    public static final MongoField<Player> DATA_LAST_LOGOUT = MongoFieldNames.raw(Player.class, DATA.path() + ".lastLogout");
    public static final MongoField<Player> DATA_LAST_SERVER = MongoFieldNames.raw(Player.class, DATA.path() + ".lastServer");
    public static final MongoField<Player> DATA_PENDING_NOTIFICATIONS = MongoFieldNames.raw(Player.class, DATA.path() + ".pendingNotifications");
    public static final MongoField<Player> DATA_LINKED_ACCOUNTS = MongoFieldNames.raw(Player.class, DATA.path() + ".linkedAccounts");
    public static final MongoField<Player> IP_COUNTRY = MongoFieldNames.raw(Player.class, IP_ADDRESSES.path() + ".$.country");
    public static final MongoField<Player> IP_REGION = MongoFieldNames.raw(Player.class, IP_ADDRESSES.path() + ".$.region");
    public static final MongoField<Player> IP_ASN = MongoFieldNames.raw(Player.class, IP_ADDRESSES.path() + ".$.asn");
    public static final MongoField<Player> IP_PROXY = MongoFieldNames.raw(Player.class, IP_ADDRESSES.path() + ".$.proxy");
    public static final MongoField<Player> IP_HOSTING = MongoFieldNames.raw(Player.class, IP_ADDRESSES.path() + ".$.hosting");
    public static final MongoField<Player> IP_LOGINS = MongoFieldNames.raw(Player.class, IP_ADDRESSES.path() + ".$.logins");
    public static final MongoField<Player> PUNISHMENT_MODIFICATIONS = MongoFieldNames.raw(Player.class, PUNISHMENTS.path() + ".$.modifications");
    public static final MongoField<Player> PUNISHMENT_NOTES = MongoFieldNames.raw(Player.class, PUNISHMENTS.path() + ".$.notes");
    public static final MongoField<Player> PUNISHMENT_EVIDENCE = MongoFieldNames.raw(Player.class, PUNISHMENTS.path() + ".$.evidence");
    public static final MongoField<Player> PUNISHMENT_STARTED = MongoFieldNames.raw(Player.class, PUNISHMENTS.path() + ".$.started");
    public static final MongoField<Player> PUNISHMENT_ATTACHED_TICKET_IDS = MongoFieldNames.raw(Player.class, PUNISHMENTS.path() + ".$.attachedTicketIds");
    public static final MongoField<Player> PUNISHMENT_DATA = MongoFieldNames.raw(Player.class, PUNISHMENTS.path() + ".$.data");

    private PlayerFields() {
    }
}
