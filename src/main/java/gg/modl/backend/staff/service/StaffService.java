package gg.modl.backend.staff.service;

import com.mongodb.client.result.DeleteResult;
import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.DynamicMongoTemplateProvider;
import gg.modl.backend.player.data.Player;
import gg.modl.backend.role.service.PermissionService;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.staff.data.Invitation;
import gg.modl.backend.staff.data.Staff;
import gg.modl.backend.staff.dto.request.AssignMinecraftPlayerRequest;
import gg.modl.backend.staff.dto.request.CreateStaffRequest;
import gg.modl.backend.staff.dto.request.UpdateStaffRequest;
import gg.modl.backend.staff.dto.response.AvailablePlayerResponse;
import gg.modl.backend.staff.dto.response.StaffResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class StaffService {
    private final DynamicMongoTemplateProvider mongoProvider;
    private final PermissionService permissionService;

    public List<StaffResponse> getAllStaff(Server server) {
        MongoTemplate template = getTemplate(server);

        List<Staff> staffMembers = template.findAll(Staff.class, CollectionName.STAFF);
        Query pendingQuery = Query.query(Criteria.where("status").is("pending"));
        List<Invitation> pendingInvitations = template.find(pendingQuery, Invitation.class, CollectionName.INVITATIONS);

        List<StaffResponse> result = new ArrayList<>();

        for (Staff staff : staffMembers) {
            result.add(toStaffResponse(staff, "Active"));
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

        return Optional.of(toStaffResponse(staff, "Active"));
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

            staff.setAssignedMinecraftUuid(null);
            staff.setAssignedMinecraftUsername(null);
            return Optional.of(toStaffResponse(staff, "Active"));
        }

        // Find player
        Query playerQuery;
        if (request.minecraftUuid() != null && !request.minecraftUuid().isEmpty()) {
            playerQuery = Query.query(Criteria.where("minecraftUuid").is(request.minecraftUuid()));
        } else {
            playerQuery = Query.query(Criteria.where("usernames.username").regex("^" + request.minecraftUsername() + "$", "i"));
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
        return updateOrCreateProfileUsername(server, email, newUsername, false);
    }

    public Optional<Staff> updateOrCreateProfileUsername(Server server, String email, String newUsername, boolean createIfNotExists) {
        MongoTemplate template = getTemplate(server);
        Query query = Query.query(Criteria.where("email").regex("^" + email + "$", "i"));
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

        return Optional.of(staff);
    }

    public Optional<Staff> getStaffByEmail(Server server, String email) {
        MongoTemplate template = getTemplate(server);
        Query query = Query.query(Criteria.where("email").regex("^" + email + "$", "i"));
        return Optional.ofNullable(template.findOne(query, Staff.class, CollectionName.STAFF));
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
