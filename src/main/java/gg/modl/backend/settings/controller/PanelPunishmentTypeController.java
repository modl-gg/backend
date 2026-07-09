package gg.modl.backend.settings.controller;

import gg.modl.backend.infrastructure.exception.ValidationException;
import gg.modl.backend.infrastructure.rest.RESTMappingV1;
import gg.modl.backend.infrastructure.rest.RequestUtil;
import gg.modl.backend.realtime.publish.RealtimeEventPublisher;
import gg.modl.backend.role.service.PermissionService;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.settings.data.PunishmentType;
import gg.modl.backend.settings.service.PunishmentTypeService;
import gg.modl.proto.modl.v1.PanelPunishmentTypesResponse;
import gg.modl.proto.modl.v1.PanelResource;
import gg.modl.proto.modl.v1.PunishmentTypeRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(RESTMappingV1.PANEL_SETTINGS + "/punishment-types")
@RequiredArgsConstructor
public class PanelPunishmentTypeController {
    private final PunishmentTypeService punishmentTypeService;
    private final RealtimeEventPublisher realtimeEventPublisher;
    private final PermissionService permissionService;
    private final Validator validator;

    @GetMapping
    public PanelPunishmentTypesResponse getPunishmentTypes(HttpServletRequest request) {
        Server server = RequestUtil.getRequestServer(request);
        List<PunishmentType> types = punishmentTypeService.getPunishmentTypes(server);
        return PanelSettingsProtoMapper.toPunishmentTypesResponse(types);
    }

    @GetMapping("/{ordinal}")
    public ResponseEntity<gg.modl.proto.modl.v1.PunishmentType> getPunishmentType(
        @PathVariable int ordinal,
        HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        return punishmentTypeService.getPunishmentTypeByOrdinal(server, ordinal)
            .map(PanelSettingsProtoMapper::toPunishmentType)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/{ordinal}")
    public gg.modl.proto.modl.v1.PunishmentType updatePunishmentType(
        @PathVariable int ordinal,
        @RequestBody PunishmentTypeRequest requestBody,
        HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        PunishmentType updatedType = PanelSettingsProtoMapper.fromPunishmentTypeRequest(requestBody);
        validate(updatedType);

        String previousName = punishmentTypeService.getPunishmentTypeByOrdinal(server, ordinal)
            .map(PunishmentType::getName)
            .orElse(null);

        PunishmentType result = punishmentTypeService.updatePunishmentType(server, ordinal, updatedType);

        if (previousName != null && !previousName.equals(result.getName())) {
            permissionService.renamePunishmentApplyPermission(server, previousName, result.getName());
        }

        invalidatePunishmentTypes(server, ordinal);
        return PanelSettingsProtoMapper.toPunishmentType(result);
    }

    @PostMapping
    public gg.modl.proto.modl.v1.PunishmentType createPunishmentType(
        @RequestBody PunishmentTypeRequest requestBody,
        HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        PunishmentType newType = PanelSettingsProtoMapper.fromPunishmentTypeRequest(requestBody);
        validate(newType);
        PunishmentType created = punishmentTypeService.createPunishmentType(server, newType);
        invalidatePunishmentTypes(server, created.getOrdinal());
        return PanelSettingsProtoMapper.toPunishmentType(created);
    }

    @PostMapping("/reset")
    public PanelPunishmentTypesResponse resetPunishmentTypes(HttpServletRequest request) {
        Server server = RequestUtil.getRequestServer(request);
        List<PunishmentType> types = punishmentTypeService.initializeDefaultTypes(server);
        invalidatePunishmentTypes(server, null);
        return PanelSettingsProtoMapper.toPunishmentTypesResponse(types);
    }

    @DeleteMapping("/{ordinal}")
    public ResponseEntity<?> deletePunishmentType(
        @PathVariable int ordinal,
        HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);

        if (ordinal < 6) {
            throw new ValidationException("Cannot delete core administrative punishment types");
        }

        boolean deleted = punishmentTypeService.deletePunishmentType(server, ordinal);
        if (!deleted) {
            return ResponseEntity.notFound().build();
        }
        invalidatePunishmentTypes(server, ordinal);
        return ResponseEntity.ok(Map.of("message", "Punishment type deleted successfully"));
    }

    private void invalidatePunishmentTypes(Server server, Integer ordinal) {
        realtimeEventPublisher.invalidatePanel(
            server,
            PanelResource.PANEL_RESOURCE_PUNISHMENT_TYPES,
            ordinal != null ? String.valueOf(ordinal) : null
        );
    }

    private <T> void validate(T target) {
        Set<ConstraintViolation<T>> violations = validator.validate(target);
        if (!violations.isEmpty()) {
            throw new ValidationException(violations.iterator().next().getMessage());
        }
    }
}
