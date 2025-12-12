package gg.modl.backend.auth.controller;

import gg.modl.backend.auth.AuthConfiguration;
import gg.modl.backend.auth.AuthResponseMessage;
import gg.modl.backend.auth.AuthService;
import gg.modl.backend.auth.session.AuthSessionData;
import gg.modl.backend.auth.session.SessionService;
import gg.modl.backend.rest.RESTMappingV1;
import gg.modl.backend.rest.RequestUtil;
import gg.modl.backend.server.data.Server;
import jakarta.mail.MessagingException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.UnsupportedEncodingException;
import java.util.Arrays;

@RestController
@RequestMapping(RESTMappingV1.PANEL_AUTH)
@RequiredArgsConstructor
@Slf4j
public class PanelAuthController {
    private final AuthService authService;
    private final SessionService sessionService;
    private final AuthConfiguration authConfiguration;

    @PostMapping("/send-email-code")
    public ResponseEntity<AuthResponse> sendEmailCode(
            HttpServletRequest request,
            @RequestBody @Valid SendEmailCodeRequest requestData,
            BindingResult bindingResult) {

        if (bindingResult.hasErrors()) {
            return ResponseEntity.badRequest().body(new AuthResponse(false, AuthResponseMessage.MISSING_EMAIL));
        }

        Server server = RequestUtil.getRequestServer(request);

        try {
            authService.sendUserLoginCode(server, requestData.email());
        } catch (MessagingException | UnsupportedEncodingException e) {
            log.error("Failed to send login code email to {}", requestData.email(), e);
            return ResponseEntity.internalServerError()
                    .body(new AuthResponse(false, AuthResponseMessage.EMAIL_SEND_ERROR));
        }

        return ResponseEntity.ok(new AuthResponse(true, AuthResponseMessage.VERIFICATION_CODE_SENT));
    }

    @PostMapping("/verify-email-code")
    public ResponseEntity<AuthResponse> verifyEmailCode(
            HttpServletRequest request,
            HttpServletResponse response,
            @RequestBody @Valid VerifyCodeRequest requestData,
            BindingResult bindingResult) {

        if (bindingResult.hasErrors()) {
            return ResponseEntity.badRequest().body(new AuthResponse(false, AuthResponseMessage.MISSING_CODE));
        }

        Server server = RequestUtil.getRequestServer(request);
        boolean valid = authService.verifyCode(server, requestData.email(), requestData.code());

        if (!valid) {
            return ResponseEntity.badRequest().body(new AuthResponse(false, AuthResponseMessage.INVALID_CODE));
        }

        AuthSessionData session = sessionService.createSession(server, requestData.email());

        Cookie sessionCookie = createSessionCookie(session.getId());
        response.addCookie(sessionCookie);

        return ResponseEntity.ok(new AuthResponse(true, AuthResponseMessage.LOGIN_SUCCESS));
    }

    @PostMapping("/logout")
    public ResponseEntity<AuthResponse> logout(HttpServletRequest request, HttpServletResponse response) {
        Server server = RequestUtil.getRequestServer(request);
        String sessionId = extractSessionId(request);

        if (sessionId != null) {
            sessionService.invalidateSession(server, sessionId);
        }

        Cookie expiredCookie = createExpiredSessionCookie();
        response.addCookie(expiredCookie);

        return ResponseEntity.ok(new AuthResponse(true, AuthResponseMessage.LOGOUT_SUCCESS));
    }

    private Cookie createSessionCookie(String sessionId) {
        Cookie cookie = new Cookie(authConfiguration.getSessionCookieName(), sessionId);

        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/");
        cookie.setMaxAge((int) authConfiguration.getSessionDurationSeconds());
        cookie.setAttribute("SameSite", "Strict");

        return cookie;
    }

    private Cookie createExpiredSessionCookie() {
        Cookie cookie = new Cookie(authConfiguration.getSessionCookieName(), "");

        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        cookie.setAttribute("SameSite", "Strict");

        return cookie;
    }

    private String extractSessionId(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }

        return Arrays.stream(cookies)
                .filter(cookie -> authConfiguration.getSessionCookieName().equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }

    public record AuthResponse(boolean success, String message) {}

    public record SendEmailCodeRequest(@Email @NotBlank String email) {}

    public record VerifyCodeRequest(@Email @NotBlank String email, @NotBlank String code) {}
}
