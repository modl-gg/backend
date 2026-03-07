package gg.modl.backend.staff.service;

import com.mongodb.client.result.DeleteResult;
import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.DynamicMongoTemplateProvider;
import gg.modl.backend.database.mongo.MongoQueries;
import gg.modl.backend.database.mongo.fields.PlayerFields;
import gg.modl.backend.database.mongo.fields.StaffFields;
import gg.modl.backend.database.mongo.fields.StaffRoleFields;
import gg.modl.backend.database.mongo.repository.PlayerMongoRepository;
import gg.modl.backend.database.mongo.repository.StaffMongoRepository;
import gg.modl.backend.database.mongo.repository.StaffRoleMongoRepository;
import gg.modl.backend.player.data.Player;
import gg.modl.backend.role.service.PermissionService;
import gg.modl.backend.server.ServerField;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.service.ServerTimestampService;
import gg.modl.backend.staff.data.Invitation;
import gg.modl.backend.staff.data.Staff;
import gg.modl.backend.staff.dto.request.AssignMinecraftPlayerRequest;
import gg.modl.backend.staff.dto.request.CreateStaffRequest;
import gg.modl.backend.staff.dto.request.UpdateStaffRequest;
import gg.modl.backend.staff.dto.response.AvailablePlayerResponse;
import gg.modl.backend.staff.dto.response.MinecraftStaffPermissionsResponse;
import gg.modl.backend.staff.dto.response.MinecraftStaffSummaryResponse;
import gg.modl.backend.staff.dto.response.StaffResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class StaffService {
    private final DynamicMongoTemplateProvider mongoProvider;
    private final StaffMongoRepository staffRepository;
    private final StaffRoleMongoRepository staffRoleRepository;
    private final PlayerMongoRepository playerRepository;
    private final PermissionService permissionService;
    private final ServerTimestampService serverTimestampService;

    public List<StaffResponse> getAllStaff(Server server) {
        MongoTemplate template = getTemplate(server);

        List<Staff> staffMembers = template.findAll(Staff.class, CollectionName.STAFF);
        Query pendingQuery = Query.query(Criteria.where("expiresAt").gt(new Date()));
        List<Invitation> pendingInvitations = template.find(pendingQuery, Invitation.class, CollectionName.INVITATIONS);

        List<StaffResponse> result = new ArrayList<>();

        // Check if the Super Admin already has a staff record
        String adminEmail = server.getAdminEmail();
        boolean superAdminFound = false;

        for (Staff staff : staffMembers) {
            result.add(toStaffResponse(staff, "Active"));
            if (adminEmail != null && adminEmail.equalsIgnoreCase(staff.getEmail())) {
                superAdminFound = true;
            }
        }

        // If Super Admin doesn't have a staff record yet, create one
        if (!superAdminFound && adminEmail != null) {
            Staff superAdmin = Staff.builder()
                    .email(adminEmail)
                    .username("Admin")
                    .role("Super Admin")
                    .createdAt(server.getCreatedAt())
                    .updatedAt(new Date())
                    .build();
            template.save(superAdmin, CollectionName.STAFF);
            result.add(0, toStaffResponse(superAdmin, "Active"));
        }

        for (Invitation invitation : pendingInvitations) {
            result.add(new StaffResponse(
                    invitation.getId(),
                    invitation.getEmail(),
                    null,
                    invitation.getRole(),
                    "Pending Invitation",
                    null,
                    null,
                    invitation.getCreatedAt()
            ));
        }

        return result;
    }

    public Optional<StaffResponse> getStaffByUsername(Server server, String username) {
        MongoTemplate template = getTemplate(server);
        Query query = Query.query(Criteria.where("username").is(username));
        Staff staff = template.findOne(query, Staff.class, CollectionName.STAFF);

        return Optional.ofNullable(staff).map(s -> toStaffResponse(s, "Active"));
    }

    public boolean checkUsernameExists(Server server, String username) {
        MongoTemplate template = getTemplate(server);
        Query query = Query.query(Criteria.where("username").is(username));
        return template.exists(query, Staff.class, CollectionName.STAFF);
    }

    public StaffResponse createStaff(Server server, CreateStaffRequest request) {
        MongoTemplate template = getTemplate(server);

        // Check for existing staff with same email or username
        Query existsQuery = new Query(new Criteria().orOperator(
                Criteria.where("email").is(request.email()),
                Criteria.where("username").is(request.username())
        ));

        if (template.exists(existsQuery, Staff.class, CollectionName.STAFF)) {
            throw new IllegalStateException("Staff member with this email or username already exists");
        }

        String role = request.role() != null ? request.role() : "Helper";

        Staff staff = Staff.builder()
                .email(request.email())
                .username(request.username())
                .role(role)
                .createdAt(new Date())
                .updatedAt(new Date())
                .build();

        template.save(staff, CollectionName.STAFF);

        return toStaffResponse(staff, "Active");
    }

    public Optional<StaffResponse> updateStaff(Server server, String username, UpdateStaffRequest request, String currentUserEmail) {
        MongoTemplate template = getTemplate(server);
        Query query = Query.query(Criteria.where("username").is(username));
        Staff staff = template.findOne(query, Staff.class, CollectionName.STAFF);

        if (staff == null) {
            return Optional.empty();
        }

        Update update = new Update().set("updatedAt", new Date());
        boolean hasChanges = false;

        if (request.email() != null && !request.email().equals(staff.getEmail())) {
            // Only allow email change if it's your own account
            if (!staff.getEmail().equalsIgnoreCase(currentUserEmail)) {
                throw new IllegalArgumentException("You can only change your own email address");
            }

            // Check if email is already in use
            Query emailQuery = Query.query(Criteria.where("email").is(request.email()));
            if (template.exists(emailQuery, Staff.class, CollectionName.STAFF)) {
                throw new IllegalStateException("Email address already in use");
            }

            update.set("email", request.email());
            hasChanges = true;
        }

        if (hasChanges) {
            template.updateFirst(query, update, Staff.class, CollectionName.STAFF);
            staff = template.findOne(query, Staff.class, CollectionName.STAFF);
        }

        return Optional.ofNullable(staff).map(s -> toStaffResponse(s, "Active"));
    }

    public boolean deleteStaff(Server server, String id, String removerEmail, String removerRole) {
        MongoTemplate template = getTemplate(server);

        // First check if it's an invitation
        Query invQuery = Query.query(Criteria.where("_id").is(id));
        DeleteResult deleteResult = template.remove(invQuery, Invitation.class, CollectionName.INVITATIONS);
        if (deleteResult.getDeletedCount() > 0) {
            return true;
        }

        // Check staff
        Query staffQuery = Query.query(Criteria.where("_id").is(id));
        Staff staffToRemove = template.findOne(staffQuery, Staff.class, CollectionName.STAFF);

        if (staffToRemove == null) {
            return false;
        }

        // Prevent removing yourself
        if (staffToRemove.getEmail().equalsIgnoreCase(removerEmail)) {
            throw new IllegalArgumentException("You cannot remove yourself");
        }

        // Prevent removing server admin
        if (server.getAdminEmail() != null &&
                staffToRemove.getEmail().equalsIgnoreCase(server.getAdminEmail())) {
            throw new IllegalArgumentException("Cannot remove the server administrator");
        }

        template.remove(staffQuery, Staff.class, CollectionName.STAFF);
        serverTimestampService.updateStaffPermissionsTimestamp(server);
        return true;
    }

    public Optional<StaffResponse> updateStaffRole(Server server, String id, String newRole, String performerEmail, String performerRole) {
        MongoTemplate template = getTemplate(server);

        Query query = Query.query(Criteria.where("_id").is(id));
        Staff staff = template.findOne(query, Staff.class, CollectionName.STAFF);

        if (staff == null) {
            return Optional.empty();
        }

        // Prevent changing role of server admin
        if (server.getAdminEmail() != null &&
                staff.getEmail().equalsIgnoreCase(server.getAdminEmail())) {
            throw new IllegalArgumentException("Cannot change the role of the server administrator");
        }

        // Prevent changing your own role
        if (staff.getEmail().equalsIgnoreCase(performerEmail)) {
            throw new IllegalArgumentException("You cannot change your own role");
        }

        Update update = new Update()
                .set("role", newRole)
                .set("updatedAt", new Date());

        template.updateFirst(query, update, Staff.class, CollectionName.STAFF);
        serverTimestampService.updateStaffPermissionsTimestamp(server);

        return Optional.of(toStaffResponse(staff, "Active"));
    }

    public List<MinecraftStaffSummaryResponse> getMinecraftStaffSummary(Server server) {
        List<Staff> allStaff = staffRepository.findAll(server);

        Map<String, Long> playerPlaytimeMap = loadPlayerPlaytimeMap(server, allStaff);
        Map<String, String> playerLastServerMap = loadPlayerLastServerMap(server, allStaff);
        Map<String, Integer> punishmentCounts = loadPunishmentCounts(server);
        Map<String, List<String>> permissionsByRole = loadPermissionsByRole(server, allStaff);

        return allStaff.stream()
                .map(staff -> {
                    int punishmentsIssuedCount = 0;
                    if (staff.getAssignedMinecraftUsername() != null && punishmentCounts.containsKey(staff.getAssignedMinecraftUsername())) {
                        punishmentsIssuedCount = punishmentCounts.get(staff.getAssignedMinecraftUsername());
                    } else if (staff.getUsername() != null && punishmentCounts.containsKey(staff.getUsername())) {
                        punishmentsIssuedCount = punishmentCounts.get(staff.getUsername());
                    }

                    return new MinecraftStaffSummaryResponse(
                            staff.getId(),
                            staff.getUsername(),
                            staff.getEmail(),
                            staff.getRole(),
                            staff.getAssignedMinecraftUuid(),
                            staff.getAssignedMinecraftUsername(),
                            permissionsByRole.getOrDefault(staff.getRole(), List.of()),
                            staff.getLastSeen(),
                            playerPlaytimeMap.getOrDefault(staff.getAssignedMinecraftUuid(), 0L),
                            playerLastServerMap.get(staff.getAssignedMinecraftUuid()),
                            punishmentsIssuedCount,
                            staff.getCreatedAt(),
                            staff.getUpdatedAt()
                    );
                })
                .toList();
    }

    public List<MinecraftStaffPermissionsResponse> getMinecraftStaffPermissions(Server server) {
        Query staffQuery = Query.query(
                MongoQueries.where(StaffFields.ASSIGNED_MINECRAFT_UUID).exists(true).ne(null).ne("")
        );
        List<Staff> staffWithMinecraft = staffRepository.find(server, staffQuery);
        Map<String, List<String>> permissionsByRole = loadPermissionsByRole(server, staffWithMinecraft);

        return staffWithMinecraft.stream()
                .map(staff -> new MinecraftStaffPermissionsResponse(
                        staff.getAssignedMinecraftUuid(),
                        staff.getAssignedMinecraftUsername() != null ? staff.getAssignedMinecraftUsername() : "",
                        staff.getUsername() != null ? staff.getUsername() : "",
                        staff.getId(),
                        staff.getRole() != null ? staff.getRole() : "",
                        permissionsByRole.getOrDefault(staff.getRole(), List.of()),
                        staff.getEmail() != null ? staff.getEmail() : ""
                ))
                .toList();
    }

    public boolean updateMinecraftStaffRole(Server server, String id, String roleName) {
        Staff staff = staffRepository.findById(server, id).orElse(null);
        if (staff == null) {
            return false;
        }

        Query roleQuery = Query.query(MongoQueries.where(StaffRoleFields.NAME).is(roleName));
        if (!staffRoleRepository.exists(server, roleQuery)) {
            throw new IllegalArgumentException("Role not found");
        }

        Staff original = staffRepository.snapshot(staff);
        staff.setRole(roleName);
        staff.setUpdatedAt(new Date());
        staffRepository.saveChanges(server, original, staff);
        serverTimestampService.updateStaffPermissionsTimestamp(server);
        return true;
    }

    public boolean markStaffDisconnected(Server server, String minecraftUuid) {
        Query query = Query.query(MongoQueries.where(StaffFields.ASSIGNED_MINECRAFT_UUID).is(minecraftUuid));
        Staff staff = staffRepository.findOne(server, query).orElse(null);
        if (staff == null) {
            return false;
        }

        Staff original = staffRepository.snapshot(staff);
        staff.setLastSeen(new Date());
        staffRepository.saveChanges(server, original, staff);
        return true;
    }

    public Optional<StaffResponse> assignMinecraftPlayer(Server server, String username, AssignMinecraftPlayerRequest request) {
        MongoTemplate template = getTemplate(server);

        Query staffQuery = Query.query(Criteria.where("username").is(username));
        Staff staff = template.findOne(staffQuery, Staff.class, CollectionName.STAFF);

        if (staff == null) {
            return Optional.empty();
        }

        // Clearing assignment
        if ((request.minecraftUuid() == null || request.minecraftUuid().isEmpty()) &&
                (request.minecraftUsername() == null || request.minecraftUsername().isEmpty())) {
            Update update = new Update()
                    .unset("assignedMinecraftUuid")
                    .unset("assignedMinecraftUsername")
                    .set("updatedAt", new Date());
            template.updateFirst(staffQuery, update, Staff.class, CollectionName.STAFF);
            serverTimestampService.updateStaffPermissionsTimestamp(server);

            staff.setAssignedMinecraftUuid(null);
            staff.setAssignedMinecraftUsername(null);
            return Optional.of(toStaffResponse(staff, "Active"));
        }

        // Find player
        Query playerQuery;
        if (request.minecraftUuid() != null && !request.minecraftUuid().isEmpty()) {
            playerQuery = Query.query(Criteria.where("minecraftUuid").is(request.minecraftUuid()));
        } else {
            String escapedUsername = Pattern.quote(request.minecraftUsername().trim());
            playerQuery = Query.query(Criteria.where("usernames.username").regex("^" + escapedUsername + "$", "i"));
        }

        Player player = template.findOne(playerQuery, Player.class, CollectionName.PLAYERS);
        if (player == null) {
            throw new IllegalArgumentException("Minecraft player not found");
        }

        // Check if already assigned to another staff
        Query existingQuery = Query.query(
                Criteria.where("assignedMinecraftUuid").is(player.getMinecraftUuid().toString())
                        .and("_id").ne(staff.getId())
        );
        Staff existingAssignment = template.findOne(existingQuery, Staff.class, CollectionName.STAFF);
        if (existingAssignment != null) {
            throw new IllegalStateException("This Minecraft player is already assigned to " + existingAssignment.getUsername());
        }

        String currentUsername = player.getUsernames().isEmpty() ? "Unknown" :
                player.getUsernames().get(player.getUsernames().size() - 1).username();

        Update update = new Update()
                .set("assignedMinecraftUuid", player.getMinecraftUuid().toString())
                .set("assignedMinecraftUsername", currentUsername)
                .set("updatedAt", new Date());

        template.updateFirst(staffQuery, update, Staff.class, CollectionName.STAFF);
        serverTimestampService.updateStaffPermissionsTimestamp(server);

        staff.setAssignedMinecraftUuid(player.getMinecraftUuid().toString());
        staff.setAssignedMinecraftUsername(currentUsername);
        return Optional.of(toStaffResponse(staff, "Active"));
    }

    public List<AvailablePlayerResponse> getAvailablePlayers(Server server) {
        MongoTemplate template = getTemplate(server);

        // Get all assigned UUIDs
        List<Staff> staffWithPlayers = template.find(
                Query.query(Criteria.where("assignedMinecraftUuid").exists(true).ne(null).ne("")),
                Staff.class,
                CollectionName.STAFF
        );

        List<String> assignedUuids = staffWithPlayers.stream()
                .map(Staff::getAssignedMinecraftUuid)
                .filter(uuid -> uuid != null && !uuid.isEmpty())
                .toList();

        // Get unassigned players
        Query playerQuery = new Query();
        if (!assignedUuids.isEmpty()) {
            playerQuery.addCriteria(Criteria.where("minecraftUuid").nin(assignedUuids));
        }
        playerQuery.limit(100);

        List<Player> players = template.find(playerQuery, Player.class, CollectionName.PLAYERS);

        return players.stream()
                .map(player -> new AvailablePlayerResponse(
                        player.getMinecraftUuid().toString(),
                        player.getUsernames().isEmpty() ? "Unknown" :
                                player.getUsernames().get(player.getUsernames().size() - 1).username()
                ))
                .toList();
    }

    private MongoTemplate getTemplate(Server server) {
        return mongoProvider.getFromDatabaseName(server.getDatabaseName());
    }

    public Optional<Staff> updateProfileUsername(Server server, String email, String newUsername) {
        return updateOrCreateProfileUsername(server, email, newUsername, false, null, null);
    }

    public Optional<Staff> updateOrCreateProfileUsername(Server server, String email, String newUsername, boolean createIfNotExists) {
        return updateOrCreateProfileUsername(server, email, newUsername, createIfNotExists, null, null);
    }

    public Optional<Staff> updateOrCreateProfileUsername(Server server, String email, String newUsername, boolean createIfNotExists, String newLanguage) {
        return updateOrCreateProfileUsername(server, email, newUsername, createIfNotExists, newLanguage, null);
    }

    public Optional<Staff> updateOrCreateProfileUsername(Server server, String email, String newUsername, boolean createIfNotExists, String newLanguage, String newDateFormat) {
        MongoTemplate template = getTemplate(server);
        Query query = Query.query(Criteria.where("email").regex("^" + Pattern.quote(email) + "$", "i"));
        Staff staff = template.findOne(query, Staff.class, CollectionName.STAFF);

        if (staff == null) {
            if (!createIfNotExists) {
                return Optional.empty();
            }
            // Create a new Staff record for Super Admin
            staff = Staff.builder()
                    .email(email)
                    .username(newUsername != null ? newUsername : "Admin")
                    .role("Super Admin")
                    .createdAt(new Date())
                    .updatedAt(new Date())
                    .build();
            template.save(staff, CollectionName.STAFF);
            return Optional.of(staff);
        }

        if (newUsername != null && !newUsername.equals(staff.getUsername())) {
            Query usernameQuery = Query.query(
                    Criteria.where("username").is(newUsername)
                            .and("_id").ne(staff.getId())
            );
            if (template.exists(usernameQuery, Staff.class, CollectionName.STAFF)) {
                throw new IllegalStateException("Username already in use");
            }

            Update update = new Update()
                    .set("username", newUsername)
                    .set("updatedAt", new Date());
            template.updateFirst(query, update, Staff.class, CollectionName.STAFF);
            staff.setUsername(newUsername);
        }

        if (newLanguage != null && List.of("en", "de", "es").contains(newLanguage)) {
            Update langUpdate = new Update().set("language", newLanguage).set("updatedAt", new Date());
            template.updateFirst(query, langUpdate, Staff.class, CollectionName.STAFF);
            staff.setLanguage(newLanguage);
        }

        if (newDateFormat != null && List.of("MM/DD/YYYY", "DD/MM/YYYY", "YYYY-MM-DD").contains(newDateFormat)) {
            Update dateFormatUpdate = new Update().set("dateFormat", newDateFormat).set("updatedAt", new Date());
            template.updateFirst(query, dateFormatUpdate, Staff.class, CollectionName.STAFF);
            staff.setDateFormat(newDateFormat);
        }

        return Optional.of(staff);
    }

    public Optional<Staff> updateEmail(Server server, String currentEmail, String newEmail, boolean isSuperAdmin) {
        MongoTemplate template = getTemplate(server);

        // Check per-server uniqueness (excluding self)
        Query emailExistsQuery = Query.query(
                Criteria.where("email").regex("^" + Pattern.quote(newEmail) + "$", "i")
        );
        Staff existingWithNewEmail = template.findOne(emailExistsQuery, Staff.class, CollectionName.STAFF);
        if (existingWithNewEmail != null && !existingWithNewEmail.getEmail().equalsIgnoreCase(currentEmail)) {
            throw new IllegalStateException("Email address already in use");
        }

        // If super admin, check global DB uniqueness before making any changes
        if (isSuperAdmin) {
            MongoTemplate globalDb = mongoProvider.getGlobalDatabase();
            Query adminEmailCheck = Query.query(
                    Criteria.where(ServerField.ADMIN_EMAIL).regex("^" + Pattern.quote(newEmail) + "$", "i")
                            .and("_id").ne(server.getId())
            );
            if (globalDb.exists(adminEmailCheck, Server.class, CollectionName.MODL_SERVERS)) {
                throw new IllegalStateException("Email address already in use");
            }
        }

        Query query = Query.query(Criteria.where("email").regex("^" + Pattern.quote(currentEmail) + "$", "i"));
        Staff staff = template.findOne(query, Staff.class, CollectionName.STAFF);

        if (staff == null) {
            if (!isSuperAdmin) {
                return Optional.empty();
            }
            // Super admin without a staff record yet — create one with new email
            staff = Staff.builder()
                    .email(newEmail)
                    .username("Admin")
                    .role("Super Admin")
                    .createdAt(new Date())
                    .updatedAt(new Date())
                    .build();
            template.save(staff, CollectionName.STAFF);
        } else {
            Update update = new Update().set("email", newEmail).set("updatedAt", new Date());
            template.updateFirst(query, update, Staff.class, CollectionName.STAFF);
            staff.setEmail(newEmail);
        }

        // Update global DB adminEmail for super admin
        if (isSuperAdmin) {
            MongoTemplate globalDb = mongoProvider.getGlobalDatabase();
            Query serverQuery = Query.query(Criteria.where("_id").is(server.getId()));
            Update serverUpdate = new Update().set(ServerField.ADMIN_EMAIL, newEmail).set("updatedAt", new Date());
            globalDb.updateFirst(serverQuery, serverUpdate, Server.class, CollectionName.MODL_SERVERS);
        }

        return Optional.of(staff);
    }

    public Optional<Staff> getStaffByEmail(Server server, String email) {
        MongoTemplate template = getTemplate(server);
        Query query = Query.query(Criteria.where("email").regex("^" + Pattern.quote(email) + "$", "i"));
        return Optional.ofNullable(template.findOne(query, Staff.class, CollectionName.STAFF));
    }

    private Map<String, Long> loadPlayerPlaytimeMap(Server server, List<Staff> allStaff) {
        List<String> assignedUuids = allStaff.stream()
                .map(Staff::getAssignedMinecraftUuid)
                .filter(uuid -> uuid != null && !uuid.isBlank())
                .distinct()
                .toList();

        if (assignedUuids.isEmpty()) {
            return Map.of();
        }

        Query playerQuery = Query.query(MongoQueries.where(PlayerFields.MINECRAFT_UUID).in(assignedUuids));
        playerQuery.fields()
                .include(PlayerFields.MINECRAFT_UUID.path())
                .include(PlayerFields.DATA_TOTAL_PLAYTIME_SECONDS.path());

        Map<String, Long> playerPlaytimeMap = new HashMap<>();
        for (Player player : playerRepository.find(server, playerQuery)) {
            if (player.getMinecraftUuid() == null || player.getData() == null) {
                continue;
            }

            Object playtimeObj = player.getData().get("totalPlaytimeSeconds");
            if (playtimeObj instanceof Number playtimeSeconds) {
                playerPlaytimeMap.put(player.getMinecraftUuid().toString(), playtimeSeconds.longValue() * 1000L);
            }
        }
        return playerPlaytimeMap;
    }

    private Map<String, String> loadPlayerLastServerMap(Server server, List<Staff> allStaff) {
        List<String> assignedUuids = allStaff.stream()
                .map(Staff::getAssignedMinecraftUuid)
                .filter(uuid -> uuid != null && !uuid.isBlank())
                .distinct()
                .toList();

        if (assignedUuids.isEmpty()) {
            return Map.of();
        }

        Query playerQuery = Query.query(MongoQueries.where(PlayerFields.MINECRAFT_UUID).in(assignedUuids));
        playerQuery.fields()
                .include(PlayerFields.MINECRAFT_UUID.path())
                .include(PlayerFields.DATA_LAST_SERVER.path());

        Map<String, String> playerLastServerMap = new HashMap<>();
        for (Player player : playerRepository.find(server, playerQuery)) {
            if (player.getMinecraftUuid() == null || player.getData() == null) {
                continue;
            }

            Object lastServerObj = player.getData().get("lastServer");
            if (lastServerObj instanceof String lastServer) {
                playerLastServerMap.put(player.getMinecraftUuid().toString(), lastServer);
            }
        }
        return playerLastServerMap;
    }

    private Map<String, Integer> loadPunishmentCounts(Server server) {
        try {
            Aggregation aggregation = Aggregation.newAggregation(
                    Aggregation.unwind(PlayerFields.PUNISHMENTS.path()),
                    Aggregation.group(PlayerFields.PUNISHMENTS.path() + ".issuerName").count().as("count")
            );
            AggregationResults<Document> results = playerRepository.aggregate(server, aggregation, Document.class);
            Map<String, Integer> punishmentCounts = new HashMap<>();
            for (Document doc : results.getMappedResults()) {
                String issuerName = doc.getString("_id");
                if (issuerName != null) {
                    punishmentCounts.put(issuerName, doc.getInteger("count", 0));
                }
            }
            return punishmentCounts;
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private Map<String, List<String>> loadPermissionsByRole(Server server, List<Staff> staffMembers) {
        Set<String> roleNames = staffMembers.stream()
                .map(Staff::getRole)
                .filter(role -> role != null && !role.isBlank())
                .collect(Collectors.toSet());
        if (roleNames.isEmpty()) {
            return Map.of();
        }

        Query roleQuery = Query.query(MongoQueries.where(StaffRoleFields.NAME).in(roleNames));
        return staffRoleRepository.find(server, roleQuery).stream()
                .collect(Collectors.toMap(
                        gg.modl.backend.role.data.StaffRole::getName,
                        role -> role.getPermissions() != null ? role.getPermissions() : List.of(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }

    private StaffResponse toStaffResponse(Staff staff, String status) {
        return new StaffResponse(
                staff.getId(),
                staff.getEmail(),
                staff.getUsername(),
                staff.getRole(),
                status,
                staff.getAssignedMinecraftUuid(),
                staff.getAssignedMinecraftUsername(),
                staff.getCreatedAt()
        );
    }
}
