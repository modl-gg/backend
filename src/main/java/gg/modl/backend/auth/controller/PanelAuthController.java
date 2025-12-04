package gg.modl.backend.auth.controller;

import gg.modl.backend.auth.AuthService;
import gg.modl.backend.rest.RESTMappingV1;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(RESTMappingV1.PANEL_AUTH)
@RequiredArgsConstructor
public class PanelAuthController {
    private final AuthService authService;

    @PostMapping("/send-email-code")
    public ResponseEntity<SendEmailCodeResponse> sendEmailCode(HttpServletRequest request/*, @RequestBody SendEmailCodeRequest requestData*/) {
        // TODO: complete
        return ResponseEntity.ok(new SendEmailCodeResponse());
    }

    public record SendEmailCodeResponse() {}

    public record SendEmailCodeRequest() {}
}
