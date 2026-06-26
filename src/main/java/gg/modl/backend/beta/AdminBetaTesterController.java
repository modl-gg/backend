package gg.modl.backend.beta;

import gg.modl.backend.admin.service.AdminAuthService;
import gg.modl.backend.infrastructure.filter.AdminAuthFilter;
import gg.modl.backend.infrastructure.rest.RESTMappingV1;
import jakarta.servlet.http.HttpServletRequest;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(RESTMappingV1.ADMIN_BETA_TESTERS)
@RequiredArgsConstructor
@Profile("staging")
public class AdminBetaTesterController {
    private static final int DEFAULT_AUDIT_LIMIT = 50;

    private final AdminBetaTesterService betaTesterService;

    @GetMapping
    public ResponseEntity<BetaApiResponse<BetaTesterListResponse>> list(
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int limit,
        @RequestParam(required = false) String search) {
        return ResponseEntity.ok(BetaApiResponse.ok(betaTesterService.list(page, limit, search)));
    }

    @PostMapping
    public ResponseEntity<BetaApiResponse<BetaTesterRecord>> create(
        @RequestBody BetaTesterCreateRequest request,
        HttpServletRequest httpRequest) {
        BetaTesterCreateRequest body = request != null ? request : new BetaTesterCreateRequest(null, null, null);
        BetaTesterRecord record = betaTesterService.create(
            body.serverName(), body.customDomain(), body.adminEmail(), actingAdminEmail(httpRequest));
        return ResponseEntity.status(201).body(BetaApiResponse.ok(record));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<BetaApiResponse<BetaTesterRecord>> revoke(
        @PathVariable String id,
        HttpServletRequest httpRequest) {
        return ResponseEntity.ok(BetaApiResponse.ok(betaTesterService.revoke(id, actingAdminEmail(httpRequest))));
    }

    @PostMapping("/{id}/reset")
    public ResponseEntity<BetaApiResponse<BetaResetResponse>> reset(
        @PathVariable String id,
        HttpServletRequest httpRequest) {
        return ResponseEntity.ok(BetaApiResponse.ok(betaTesterService.reset(id, actingAdminEmail(httpRequest))));
    }

    @PostMapping("/reset-all")
    public CompletableFuture<ResponseEntity<BetaApiResponse<BetaResetAllResponse>>> resetAll(HttpServletRequest httpRequest) {
        return betaTesterService.resetAll(actingAdminEmail(httpRequest))
            .thenApply(results -> ResponseEntity.ok(BetaApiResponse.ok(new BetaResetAllResponse(results))));
    }

    @GetMapping("/{id}")
    public ResponseEntity<BetaApiResponse<BetaTesterRecord>> get(@PathVariable String id) {
        return ResponseEntity.ok(BetaApiResponse.ok(betaTesterService.get(id)));
    }

    @GetMapping("/{id}/audit")
    public ResponseEntity<BetaApiResponse<BetaAuditResponse>> audit(
        @PathVariable String id,
        @RequestParam(defaultValue = "" + DEFAULT_AUDIT_LIMIT) int limit) {
        return ResponseEntity.ok(BetaApiResponse.ok(betaTesterService.audit(id, limit)));
    }

    @ExceptionHandler(BetaRequestException.class)
    public ResponseEntity<BetaApiResponse<Object>> handleBetaRequest(BetaRequestException ex) {
        return ResponseEntity.status(ex.getStatus()).body(BetaApiResponse.error(ex.getMessage()));
    }

    private String actingAdminEmail(HttpServletRequest request) {
        Object attribute = request.getAttribute(AdminAuthFilter.ADMIN_SESSION_ATTR);
        if (attribute instanceof AdminAuthService.AdminSession session) {
            return session.email();
        }
        throw new BetaRequestException("Admin authentication required.", HttpStatus.UNAUTHORIZED);
    }
}
