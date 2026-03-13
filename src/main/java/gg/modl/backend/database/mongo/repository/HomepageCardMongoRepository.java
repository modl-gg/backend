package gg.modl.backend.database.mongo.repository;

import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.mongo.AbstractServerMongoRepository;
import gg.modl.backend.database.mongo.MongoQueries;
import gg.modl.backend.database.mongo.TenantMongoAccess;
import gg.modl.backend.database.mongo.fields.HomepageCardFields;
import gg.modl.backend.homepage.data.HomepageCard;
import gg.modl.backend.server.data.Server;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

@Repository
public class HomepageCardMongoRepository extends AbstractServerMongoRepository<HomepageCard> {
    public HomepageCardMongoRepository(TenantMongoAccess tenantMongoAccess) {
        super(HomepageCard.class, CollectionName.HOMEPAGE_CARDS, tenantMongoAccess);
    }

    public boolean hasAny(Server server) {
        return count(server, new Query()) > 0;
    }

    public List<HomepageCard> findAllOrdered(Server server) {
        return find(server, new Query().with(MongoQueries.sort(Sort.Direction.ASC, HomepageCardFields.ORDINAL)));
    }

    public List<HomepageCard> findVisibleOrdered(Server server) {
        Query query = Query.query(MongoQueries.where(HomepageCardFields.IS_ENABLED).is(true))
            .with(MongoQueries.sort(Sort.Direction.ASC, HomepageCardFields.ORDINAL));
        return find(server, query);
    }

    public Optional<HomepageCard> findByCardId(Server server, String id) {
        return findById(server, id);
    }

    public int findMaxOrdinal(Server server) {
        Query query = new Query()
            .with(MongoQueries.sort(Sort.Direction.DESC, HomepageCardFields.ORDINAL))
            .limit(1);
        return findOne(server, query)
            .map(HomepageCard::getOrdinal)
            .orElse(-1);
    }

    public Optional<HomepageCard> updateCard(
        Server server,
        String id,
        String title,
        String description,
        String icon,
        String iconColor,
        String actionType,
        String actionUrl,
        String actionButtonText,
        String categoryId,
        String backgroundColor,
        Boolean isEnabled,
        Date updatedAt
    ) {
        Update update = new Update().set(HomepageCardFields.UPDATED_AT, updatedAt);
        if (title != null) {
            update.set(HomepageCardFields.TITLE, title);
        }
        if (description != null) {
            update.set(HomepageCardFields.DESCRIPTION, description);
        }
        if (icon != null) {
            update.set(HomepageCardFields.ICON, icon);
        }
        if (iconColor != null) {
            update.set(HomepageCardFields.ICON_COLOR, iconColor);
        }
        if (actionType != null) {
            update.set(HomepageCardFields.ACTION_TYPE, actionType);
        }
        if (actionUrl != null) {
            update.set(HomepageCardFields.ACTION_URL, actionUrl);
        }
        if (actionButtonText != null) {
            update.set(HomepageCardFields.ACTION_BUTTON_TEXT, actionButtonText);
        }
        if (categoryId != null) {
            update.set(HomepageCardFields.CATEGORY_ID, categoryId);
        }
        if (backgroundColor != null) {
            update.set(HomepageCardFields.BACKGROUND_COLOR, backgroundColor);
        }
        if (isEnabled != null) {
            update.set(HomepageCardFields.IS_ENABLED, isEnabled);
        }

        HomepageCard updated = findAndModify(
            server,
            Query.query(MongoQueries.where(HomepageCardFields.ID).is(id)),
            update,
            FindAndModifyOptions.options().returnNew(true)
        );
        return Optional.ofNullable(updated);
    }

    public boolean deleteByCardId(Server server, String id) {
        return remove(server, Query.query(MongoQueries.where(HomepageCardFields.ID).is(id))).getDeletedCount() > 0;
    }

    public void reorderCards(Server server, List<String> ids) {
        if (ids.isEmpty()) return;

        MongoTemplate template = serverTemplate(server);
        BulkOperations bulk = template.bulkOps(BulkOperations.BulkMode.UNORDERED, collectionName());
        for (int index = 0; index < ids.size(); index++) {
            Query query = Query.query(Criteria.where("_id").is(new ObjectId(ids.get(index))));
            Update update = new Update().set(HomepageCardFields.ORDINAL, index);
            bulk.updateOne(query, update);
        }
        bulk.execute();
    }
}
