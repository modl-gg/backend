package gg.modl.backend.admin.service;

import gg.modl.backend.admin.data.SecurityEvent;
import gg.modl.backend.database.mongo.repository.SecurityEventMongoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminSecurityServiceTest {

    @Mock
    private SecurityEventMongoRepository securityEventRepository;

    private AdminSecurityService adminSecurityService;

    @BeforeEach
    void setUp() {
        adminSecurityService = new AdminSecurityService(securityEventRepository);
    }

    @Test
    void getSecurityEventsBuildsFilteredPagedQuery() {
        SecurityEvent event = new SecurityEvent();
        event.setType("login_attempt");
        event.setTimestamp(new Date());
        when(securityEventRepository.findSecurityEvents(
                "login_attempt", "high", "gateway", "blocked", new Date(1000L), new Date(2000L), 25, 25
        )).thenReturn(List.of(event));
        when(securityEventRepository.countSecurityEvents(
                "login_attempt", "high", "gateway", "blocked", new Date(1000L), new Date(2000L)
        )).thenReturn(1L);

        Map<String, Object> response = adminSecurityService.getSecurityEvents(
                2,
                25,
                "login_attempt",
                "high",
                "gateway",
                "blocked",
                "1000",
                "2000"
        );

        verify(securityEventRepository).findSecurityEvents(
                "login_attempt", "high", "gateway", "blocked", new Date(1000L), new Date(2000L), 25, 25
        );
        verify(securityEventRepository).countSecurityEvents(
                "login_attempt", "high", "gateway", "blocked", new Date(1000L), new Date(2000L)
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.get("data");
        @SuppressWarnings("unchecked")
        Map<String, Object> pagination = (Map<String, Object>) data.get("pagination");
        assertEquals(2, pagination.get("page"));
        assertEquals(25, pagination.get("limit"));
        assertEquals(1L, pagination.get("total"));
    }
}
