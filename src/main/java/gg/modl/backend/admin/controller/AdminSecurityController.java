package gg.modl.backend.admin.controller;

import gg.modl.backend.admin.service.AdminSecurityService;
import gg.modl.backend.infrastructure.rest.RESTMappingV1;
import gg.modl.backend.infrastructure.validation.RequestValidationLimits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(RESTMappingV1.ADMIN_SECURITY)
@RequiredArgsConstructor
@Validated
public class AdminSecurityController {
    private final AdminSecurityService adminSecurityService;

    @GetMapping("/events")
    public ResponseEntity<?> getSecurityEvents(
        @RequestParam(defaultValue = "1") @Min(RequestValidationLimits.PAGINATION_PAGE_MIN) int page,
        @RequestParam(defaultValue = "50") @Min(RequestValidationLimits.PAGINATION_LIMIT_MIN) @Max(RequestValidationLimits.PAGINATION_LIMIT_MAX) int limit,
        @RequestParam(required = false) String type,
        @RequestParam(required = false) String severity,
        @RequestParam(required = false) String source,
        @RequestParam(required = false) String search,
        @RequestParam(required = false) String startDate,
        @RequestParam(required = false) String endDate) {
        return ResponseEntity.ok(AdminSecurityProtoMapper.toEventsResponse(adminSecurityService.getSecurityEvents(
            page,
            limit,
            type,
            severity,
            source,
            search,
            startDate,
            endDate
        )));
    }

    @GetMapping("/summary")
    public ResponseEntity<?> getSecuritySummary() {
        return ResponseEntity.ok(AdminSecurityProtoMapper.toSummaryResponse(adminSecurityService.getSecuritySummary()));
    }

}
