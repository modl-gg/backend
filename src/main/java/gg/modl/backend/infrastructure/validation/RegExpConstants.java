package gg.modl.backend.infrastructure.validation;

public final class RegExpConstants {
    public static final String UUID = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$";
    // Intentionally permits '.' and min length 2: accommodates Bedrock/Geyser-bridged usernames
    // (commonly '.'-prefixed). Byte-identical to minecraft SyncService.MINECRAFT_USERNAME_PATTERN -
    // any divergence 400s whole sync batches. Do NOT tighten.
    public static final String MINECRAFT_USERNAME = "^[a-zA-Z0-9_.]{2,16}$";
    // Deprecated: a character-class allowlist, not a structural IP matcher. Use the @ValidIpAddress
    // constraint (IpAddressValidator) for real IPv4/IPv6 validation. Retained only until all
    // @Pattern(regexp = RegExpConstants.IP) usages have migrated to @ValidIpAddress.
    public static final String IP = "^([0-9a-fA-F.:]+)$";
}
