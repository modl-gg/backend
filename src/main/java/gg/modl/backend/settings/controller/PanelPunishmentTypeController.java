package gg.modl.backend.settings.controller;

import gg.modl.backend.rest.RESTMappingV1;
import gg.modl.backend.rest.RequestUtil;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.settings.data.PunishmentType;
import gg.modl.backend.settings.dto.request.PunishmentTypeRequest;
import gg.modl.backend.settings.service.PunishmentTypeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
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

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping(RESTMappingV1.PANEL_SETTINGS + "/punishment-types")
@RequiredArgsConstructor
public class PanelPunishmentTypeController {
    private final PunishmentTypeService punishmentTypeService;

    @GetMapping
    public ResponseEntity<List<PunishmentType>> getPunishmentTypes(HttpServletRequest request) {
        Server server = RequestUtil.getRequestServer(request);
        List<PunishmentType> types = punishmentTypeService.getPunishmentTypes(server);
        return ResponseEntity.ok(types);
    }

    @GetMapping("/{ordinal}")
    public ResponseEntity<PunishmentType> getPunishmentType(
            @PathVariable int ordinal,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        return punishmentTypeService.getPunishmentTypeByOrdinal(server, ordinal)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/{ordinal}")
    public ResponseEntity<PunishmentType> updatePunishmentType(
            @PathVariable int ordinal,
            @RequestBody @Valid PunishmentTypeRequest requestBody,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        PunishmentType updatedType = requestBody.toPunishmentType();

        try {
            PunishmentType result = punishmentTypeService.updatePunishmentType(server, ordinal, updatedType);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping
    public ResponseEntity<PunishmentType> createPunishmentType(
            @RequestBody @Valid PunishmentTypeRequest requestBody,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        PunishmentType newType = requestBody.toPunishmentType();
        PunishmentType created = punishmentTypeService.createPunishmentType(server, newType);
        return ResponseEntity.ok(created);
    }

    @PostMapping("/reset")
    public ResponseEntity<List<PunishmentType>> resetPunishmentTypes(HttpServletRequest request) {
        Server server = RequestUtil.getRequestServer(request);
        List<PunishmentType> types = punishmentTypeService.initializeDefaultTypes(server);
        return ResponseEntity.ok(types);
    }

    @DeleteMapping("/{ordinal}")
    public ResponseEntity<?> deletePunishmentType(
            @PathVariable int ordinal,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);

        if (ordinal < 6) {
            return ResponseEntity.badRequest().body(Map.of("error", "Cannot delete core administrative punishment types"));
        }

        try {
            boolean deleted = punishmentTypeService.deletePunishmentType(server, ordinal);
            if (deleted) {
                return ResponseEntity.ok(Map.of("message", "Punishment type deleted successfully"));
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
