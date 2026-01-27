package gg.modl.backend.rest;

public final class RESTMappingV1 {
    private static final String V1 = "/v1";
    public static final String PREFIX_PANEL = V1 + "/panel";
    public static final String PREFIX_ADMIN = V1 + "/admin";
    public static final String PREFIX_PUBLIC = V1 + "/public";
    public static final String PREFIX_MINECRAFT = V1 + "/minecraft";

    private static final String SERVER = "/server";
    public static final String PUBLIC_SERVER = PREFIX_PUBLIC + SERVER;
    public static final String PANEL_SERVER = PREFIX_PANEL + SERVER;

    private static final String PLAYER = "/player";
    public static final String MINECRAFT_PLAYER = PREFIX_MINECRAFT + PLAYER;

    private static final String PLAYERS = "/players";
    public static final String PANEL_PLAYERS = PREFIX_PANEL + PLAYERS;
    public static final String PUBLIC_PLAYERS = PREFIX_PUBLIC + PLAYERS;
    public static final String MINECRAFT_PLAYERS = PREFIX_MINECRAFT + PLAYERS;

    private static final String AUTH = "/auth";
    public static final String PANEL_AUTH = PREFIX_PANEL + AUTH;

    private static final String SETTINGS = "/settings";
    public static final String PANEL_SETTINGS = PREFIX_PANEL + SETTINGS;

    // Tickets
    private static final String TICKETS = "/tickets";
    public static final String PANEL_TICKETS = PREFIX_PANEL + TICKETS;
    public static final String PUBLIC_TICKETS = PREFIX_PUBLIC + TICKETS;

    private static final String TICKET_SUBSCRIPTIONS = "/ticket-subscriptions";
    public static final String PANEL_TICKET_SUBSCRIPTIONS = PREFIX_PANEL + TICKET_SUBSCRIPTIONS;

    // Staff & Roles
    private static final String STAFF = "/staff";
    public static final String PANEL_STAFF = PREFIX_PANEL + STAFF;

    private static final String ROLES = "/roles";
    public static final String PANEL_ROLES = PREFIX_PANEL + ROLES;

    // Analytics, Audit & Dashboard
    private static final String ANALYTICS = "/analytics";
    public static final String PANEL_ANALYTICS = PREFIX_PANEL + ANALYTICS;

    private static final String AUDIT = "/audit";
    public static final String PANEL_AUDIT = PREFIX_PANEL + AUDIT;

    private static final String DASHBOARD = "/dashboard";
    public static final String PANEL_DASHBOARD = PREFIX_PANEL + DASHBOARD;

    // Billing & Storage
    private static final String BILLING = "/billing";
    public static final String PANEL_BILLING = PREFIX_PANEL + BILLING;

    private static final String MEDIA = "/media";
    public static final String PANEL_MEDIA = PREFIX_PANEL + MEDIA;

    private static final String STORAGE = "/storage";
    public static final String PANEL_STORAGE = PREFIX_PANEL + STORAGE;

    private static final String DOMAIN = "/domain";
    public static final String PANEL_DOMAIN = PREFIX_PANEL + DOMAIN;

    // Knowledgebase & Homepage
    private static final String KNOWLEDGEBASE = "/knowledgebase";
    public static final String PANEL_KNOWLEDGEBASE = PREFIX_PANEL + KNOWLEDGEBASE;
    public static final String PUBLIC_KNOWLEDGEBASE = PREFIX_PUBLIC + KNOWLEDGEBASE;

    private static final String HOMEPAGE_CARDS = "/homepage-cards";
    public static final String PANEL_HOMEPAGE_CARDS = PREFIX_PANEL + HOMEPAGE_CARDS;
    public static final String PUBLIC_HOMEPAGE_CARDS = PREFIX_PUBLIC + HOMEPAGE_CARDS;

    // Webhooks
    private static final String WEBHOOKS = "/webhooks";
    public static final String WEBHOOKS_STRIPE = V1 + WEBHOOKS + "/stripe";

    // Appeals
    private static final String APPEALS = "/appeals";
    public static final String PANEL_APPEALS = PREFIX_PANEL + APPEALS;
    public static final String PUBLIC_APPEALS = PREFIX_PUBLIC + APPEALS;

    // Public settings and media
    public static final String PUBLIC_SETTINGS = PREFIX_PUBLIC + SETTINGS;
    public static final String PUBLIC_MEDIA = PREFIX_PUBLIC + MEDIA;

    // Registration
    private static final String REGISTRATION = "/registration";
    public static final String PUBLIC_REGISTRATION = PREFIX_PUBLIC + REGISTRATION;

    // Logs
    private static final String LOGS = "/logs";
    public static final String PANEL_LOGS = PREFIX_PANEL + LOGS;

    // Migration
    private static final String MIGRATION = "/migration";
    public static final String PANEL_MIGRATION = PREFIX_PANEL + MIGRATION;
    public static final String MINECRAFT_MIGRATION = PREFIX_MINECRAFT + MIGRATION;

    // Minecraft API endpoints
    private static final String PUNISHMENTS = "/punishments";
    public static final String MINECRAFT_PUNISHMENTS = PREFIX_MINECRAFT + PUNISHMENTS;

    // Public punishment endpoint
    private static final String PUNISHMENT = "/punishment";
    public static final String PUBLIC_PUNISHMENT = PREFIX_PUBLIC + PUNISHMENT;
    public static final String MINECRAFT_STAFF = PREFIX_MINECRAFT + STAFF;
    private static final String NOTIFICATIONS = "/notifications";
    public static final String MINECRAFT_NOTIFICATIONS = PREFIX_MINECRAFT + NOTIFICATIONS;
    private static final String REPORTS = "/reports";
    public static final String MINECRAFT_REPORTS = PREFIX_MINECRAFT + REPORTS;
    public static final String MINECRAFT_TICKETS = PREFIX_MINECRAFT + TICKETS;
    public static final String MINECRAFT_DASHBOARD = PREFIX_MINECRAFT + DASHBOARD;
    public static final String MINECRAFT_ROLES = PREFIX_MINECRAFT + ROLES;

    // Health check
    public static final String HEALTH = V1 + "/health";

    // Admin routes
    public static final String ADMIN_AUTH = PREFIX_ADMIN + "/auth";

    private static final String SERVERS = "/servers";
    public static final String ADMIN_SERVERS = PREFIX_ADMIN + SERVERS;

    public static final String ADMIN_ANALYTICS = PREFIX_ADMIN + ANALYTICS;

    private static final String MONITORING = "/monitoring";
    public static final String ADMIN_MONITORING = PREFIX_ADMIN + MONITORING;

    private static final String SYSTEM = "/system";
    public static final String ADMIN_SYSTEM = PREFIX_ADMIN + SYSTEM;

    private static final String SECURITY = "/security";
    public static final String ADMIN_SECURITY = PREFIX_ADMIN + SECURITY;
}
