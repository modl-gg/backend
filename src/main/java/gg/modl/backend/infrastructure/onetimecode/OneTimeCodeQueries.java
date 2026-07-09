package gg.modl.backend.infrastructure.onetimecode;

import java.util.Date;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

public final class OneTimeCodeQueries {
    public static final int MAX_FAILED_ATTEMPTS = 5;

    private OneTimeCodeQueries() {
    }

    public static Query matchActiveUnlockedCode(Criteria identity, OneTimeCodeFieldNames fields, String codeHash, Date now) {
        return Query.query(new Criteria().andOperator(
            identity,
            Criteria.where(fields.expiresAt()).gt(now),
            Criteria.where(fields.codeHash()).is(codeHash),
            Criteria.where(fields.failedAttempts()).lt(MAX_FAILED_ATTEMPTS)
        ));
    }

    public static Query matchActive(Criteria identity, OneTimeCodeFieldNames fields, Date now) {
        return Query.query(new Criteria().andOperator(
            identity,
            Criteria.where(fields.expiresAt()).gt(now)
        ));
    }

    public static Update incrementFailedAttempts(OneTimeCodeFieldNames fields) {
        return new Update().inc(fields.failedAttempts(), 1);
    }
}
