package gg.modl.backend.database.mongo.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import gg.modl.backend.alert.data.SystemAlert;
import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.mongo.TenantMongoAccess;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

class SystemAlertMongoRepositoryTest {
    @Test
    void findVisibleMatchesPermanentAndNonExpiredAlerts() {
        TenantMongoAccess tenantMongoAccess = mock(TenantMongoAccess.class);
        MongoTemplate template = mock(MongoTemplate.class);
        when(tenantMongoAccess.global()).thenReturn(template);
        when(template.find(any(Query.class), eq(SystemAlert.class), eq(CollectionName.SYSTEM_ALERTS)))
            .thenReturn(List.of());
        SystemAlertMongoRepository repository = new SystemAlertMongoRepository(tenantMongoAccess);

        repository.findVisible(new Date());

        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        verify(template).find(queryCaptor.capture(), eq(SystemAlert.class), eq(CollectionName.SYSTEM_ALERTS));
        String queryText = queryCaptor.getValue().getQueryObject().toString();
        assertThat(queryText).contains("$or");
        assertThat(queryText).contains("expiresAt");
        assertThat(queryText).contains("$gt");
    }

    @Test
    void updateAlertUnsetsExpiryWhenClearedToPermanent() {
        TenantMongoAccess tenantMongoAccess = mock(TenantMongoAccess.class);
        MongoTemplate template = mock(MongoTemplate.class);
        when(tenantMongoAccess.global()).thenReturn(template);
        when(template.findAndModify(any(Query.class), any(Update.class),
            any(FindAndModifyOptions.class), eq(SystemAlert.class), eq(CollectionName.SYSTEM_ALERTS)))
            .thenReturn(null);
        SystemAlertMongoRepository repository = new SystemAlertMongoRepository(tenantMongoAccess);

        repository.updateAlert("alert-1", null, null, null, true, null, new Date(), "admin@example.com");

        ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
        verify(template).findAndModify(any(Query.class), updateCaptor.capture(),
            any(FindAndModifyOptions.class), eq(SystemAlert.class), eq(CollectionName.SYSTEM_ALERTS));
        String updateText = updateCaptor.getValue().getUpdateObject().toString();
        assertThat(updateText).contains("$unset");
        assertThat(updateText).contains("expiresAt");
    }

    @Test
    void updateAlertSetsExpiryWhenPresentAndNonNull() {
        TenantMongoAccess tenantMongoAccess = mock(TenantMongoAccess.class);
        MongoTemplate template = mock(MongoTemplate.class);
        when(tenantMongoAccess.global()).thenReturn(template);
        when(template.findAndModify(any(Query.class), any(Update.class),
            any(FindAndModifyOptions.class), eq(SystemAlert.class), eq(CollectionName.SYSTEM_ALERTS)))
            .thenReturn(null);
        SystemAlertMongoRepository repository = new SystemAlertMongoRepository(tenantMongoAccess);

        Date expiresAt = new Date();
        repository.updateAlert("alert-1", null, null, null, true, expiresAt, new Date(), "admin@example.com");

        ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
        verify(template).findAndModify(any(Query.class), updateCaptor.capture(),
            any(FindAndModifyOptions.class), eq(SystemAlert.class), eq(CollectionName.SYSTEM_ALERTS));
        String updateText = updateCaptor.getValue().getUpdateObject().toString();
        assertThat(updateText).contains("$set");
        assertThat(updateText).contains("expiresAt");
        assertThat(updateText).doesNotContain("$unset");
    }

    @Test
    void updateAlertLeavesExpiryUntouchedWhenAbsent() {
        TenantMongoAccess tenantMongoAccess = mock(TenantMongoAccess.class);
        MongoTemplate template = mock(MongoTemplate.class);
        when(tenantMongoAccess.global()).thenReturn(template);
        when(template.findAndModify(any(Query.class), any(Update.class),
            any(FindAndModifyOptions.class), eq(SystemAlert.class), eq(CollectionName.SYSTEM_ALERTS)))
            .thenReturn(null);
        SystemAlertMongoRepository repository = new SystemAlertMongoRepository(tenantMongoAccess);

        repository.updateAlert("alert-1", null, null, null, false, null, new Date(), "admin@example.com");

        ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
        verify(template).findAndModify(any(Query.class), updateCaptor.capture(),
            any(FindAndModifyOptions.class), eq(SystemAlert.class), eq(CollectionName.SYSTEM_ALERTS));
        String updateText = updateCaptor.getValue().getUpdateObject().toString();
        assertThat(updateText).doesNotContain("$unset");
        assertThat(updateText).doesNotContain("expiresAt");
    }
}
