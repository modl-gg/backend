package gg.modl.backend.registration;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import gg.modl.backend.beta.SubdomainValidator;
import gg.modl.backend.email.EmailService;
import gg.modl.backend.infrastructure.config.ModlProperties;
import gg.modl.backend.infrastructure.ratelimit.BucketPool;
import gg.modl.backend.infrastructure.turnstile.TurnstileService;
import gg.modl.backend.server.ServerService;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.data.ServerPlan;
import gg.modl.backend.settings.service.ApiKeySettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class RegistrationServiceRateLimitTest {

    private ServerService serverService;
    private SubdomainValidator subdomainValidator;
    private RegistrationService service;

    @BeforeEach
    void setUp() {
        EmailService emailService = mock(EmailService.class);
        ApiKeySettingsService apiKeySettingsService = mock(ApiKeySettingsService.class);
        ModlProperties modlProperties = mock(ModlProperties.class);
        serverService = mock(ServerService.class);
        TurnstileService turnstileService = mock(TurnstileService.class);
        subdomainValidator = mock(SubdomainValidator.class);

        when(modlProperties.getAppDomain()).thenReturn("modl.gg");
        when(subdomainValidator.normalize(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        when(subdomainValidator.matchesFormat(anyString())).thenReturn(true);
        when(subdomainValidator.isReserved(anyString())).thenReturn(false);
        when(serverService.doesServerExist(anyString(), anyString(), anyString()))
            .thenReturn(new ServerService.ServerExistResult(false, false, false));
        when(serverService.createServer(anyString(), anyString(), anyString(), anyString(), any()))
            .thenReturn(mock(Server.class));

        service = new RegistrationService(emailService, apiKeySettingsService, modlProperties, serverService,
            turnstileService, subdomainValidator, new BucketPool());
    }

    private RegistrationService.RegistrationCommand command(String clientIp) {
        return new RegistrationService.RegistrationCommand(RegistrationService.RegistrationChannel.WEB,
            false, null, clientIp, "owner@example.com", "server", "sub");
    }

    @Test
    void firstRegistrationUnderLimitSucceeds() {
        RegistrationService.RegistrationOutcome outcome = service.performRegistration(command("198.51.100.1"));

        assertInstanceOf(RegistrationService.RegistrationOutcome.Created.class, outcome);
    }

    @Test
    void secondRegistrationFromSameIpIsRejectedWithFrozenSurface() {
        String clientIp = "198.51.100.2";
        service.performRegistration(command(clientIp));

        RegistrationService.RegistrationOutcome outcome = service.performRegistration(command(clientIp));

        RegistrationService.RegistrationOutcome.Rejected rejected =
            assertInstanceOf(RegistrationService.RegistrationOutcome.Rejected.class, outcome);
        assertTrue(rejected.rejection().status() == HttpStatus.TOO_MANY_REQUESTS);
        String message = rejected.rejection().message();
        assertTrue(message.startsWith("Rate limit exceeded."));
        assertTrue(message.contains("You can only register one server every 10 minutes."));
        assertTrue(message.contains("Please try again in"));
        assertTrue(message.contains("minute(s)."));
    }

    @Test
    void limitIsIsolatedPerClientIp() {
        String exhausted = "198.51.100.3";
        service.performRegistration(command(exhausted));
        assertInstanceOf(RegistrationService.RegistrationOutcome.Rejected.class,
            service.performRegistration(command(exhausted)));

        RegistrationService.RegistrationOutcome fresh = service.performRegistration(command("198.51.100.4"));

        assertInstanceOf(RegistrationService.RegistrationOutcome.Created.class, fresh);
    }
}
