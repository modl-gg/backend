package gg.modl.backend.admin.controller;

import gg.modl.backend.admin.service.AdminSecurityService;
import gg.modl.backend.rest.RESTMappingV1;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(RESTMappingV1.ADMIN_SECURITY)
@RequiredArgsConstructor
public class AdminSecurityController {
    private final AdminSecurityService adminSecurityService;

    @GetMapping("/events")
    public ResponseEntity<?> getSecurityEvents(
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "50") int limit,
        @RequestParam(required = false) String type,
        @RequestParam(required = false) String severity,
        @RequestParam(required = false) String source,
        @RequestParam(required = false) String search,
        @RequestParam(required = false) String startDate,
        @RequestParam(required = false) String endDate) {
        return ResponseEntity.ok(adminSecurityService.getSecurityEvents(
            page,
            limit,
            type,
            severity,
            source,
            search,
            startDate,
            endDate
        ));
    }

    @GetMapping("/summary")
    public ResponseEntity<?> getSecuritySummary() {
        return ResponseEntity.ok(adminSecurityService.getSecuritySummary());
    }

    @PostMapping("/test")
    public ResponseEntity<?> testSecurityConfig() {
        return ResponseEntity.ok(adminSecurityService.testSecurityConfig());
    }
}
