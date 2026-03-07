package gg.modl.backend.admin.service;

import gg.modl.backend.database.mongo.repository.SecurityEventMongoRepository;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.query.Query;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
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
        when(securityEventRepository.find(any(Query.class))).thenReturn(List.of(new Document("type", "login_attempt")));
        when(securityEventRepository.count(any(Query.class))).thenReturn(1L);

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

        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        verify(securityEventRepository).find(queryCaptor.capture());
        Query query = queryCaptor.getValue();

        assertEquals(25L, query.getSkip());
        assertEquals(25, query.getLimit());
        assertEquals(-1, query.getSortObject().get("timestamp"));
        assertNotNull(query.getQueryObject().get("$and"));

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.get("data");
        @SuppressWarnings("unchecked")
        Map<String, Object> pagination = (Map<String, Object>) data.get("pagination");
        assertEquals(2, pagination.get("page"));
        assertEquals(25, pagination.get("limit"));
        assertEquals(1L, pagination.get("total"));
    }
}
