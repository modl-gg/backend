package gg.modl.backend.homepage.service;

import com.mongodb.client.result.DeleteResult;
import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.DynamicMongoTemplateProvider;
import gg.modl.backend.homepage.data.HomepageCard;
import gg.modl.backend.homepage.dto.request.CreateCardRequest;
import gg.modl.backend.homepage.dto.request.UpdateCardRequest;
import gg.modl.backend.server.data.Server;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class HomepageCardService {
    private final DynamicMongoTemplateProvider mongoProvider;

    public List<HomepageCard> getAllCards(Server server) {
        MongoTemplate template = getTemplate(server);
        Query query = new Query().with(Sort.by(Sort.Direction.ASC, "ordinal"));
        return template.find(query, HomepageCard.class, CollectionName.HOMEPAGE_CARDS);
    }

    public List<HomepageCard> getVisibleCards(Server server) {
        MongoTemplate template = getTemplate(server);
        Query query = Query.query(Criteria.where("is_enabled").is(true))
                .with(Sort.by(Sort.Direction.ASC, "ordinal"));
        return template.find(query, HomepageCard.class, CollectionName.HOMEPAGE_CARDS);
    }

    public Optional<HomepageCard> getCardById(Server server, String id) {
        MongoTemplate template = getTemplate(server);
        Query query = Query.query(Criteria.where("_id").is(id));
        return Optional.ofNullable(template.findOne(query, HomepageCard.class, CollectionName.HOMEPAGE_CARDS));
    }

    public HomepageCard createCard(Server server, CreateCardRequest request) {
        MongoTemplate template = getTemplate(server);

        int maxOrdinal = getMaxOrdinal(template);

        HomepageCard card = HomepageCard.builder()
                .title(request.title())
                .description(request.description())
                .icon(request.icon())
                .iconColor(request.iconColor())
                .actionType(request.actionType())
                .actionUrl(request.actionUrl())
                .actionButtonText(request.actionButtonText())
                .categoryId(request.categoryId())
                .backgroundColor(request.backgroundColor())
                .ordinal(maxOrdinal + 1)
                .isEnabled(request.isEnabled() != null ? request.isEnabled() : true)
                .createdAt(new Date())
                .updatedAt(new Date())
                .build();

        template.save(card, CollectionName.HOMEPAGE_CARDS);
        return card;
    }

    public Optional<HomepageCard> updateCard(Server server, String id, UpdateCardRequest request) {
        MongoTemplate template = getTemplate(server);
        Query query = Query.query(Criteria.where("_id").is(id));

        HomepageCard card = template.findOne(query, HomepageCard.class, CollectionName.HOMEPAGE_CARDS);
        if (card == null) {
            return Optional.empty();
        }

        Update update = new Update().set("updated_at", new Date());

        if (request.title() != null) {
            update.set("title", request.title());
        }
        if (request.description() != null) {
            update.set("description", request.description());
        }
        if (request.icon() != null) {
            update.set("icon", request.icon());
        }
        if (request.iconColor() != null) {
            update.set("icon_color", request.iconColor());
        }
        if (request.actionType() != null) {
            update.set("action_type", request.actionType());
        }
        if (request.actionUrl() != null) {
            update.set("action_url", request.actionUrl());
        }
        if (request.actionButtonText() != null) {
            update.set("action_button_text", request.actionButtonText());
        }
        if (request.categoryId() != null) {
            update.set("category_id", request.categoryId());
        }
        if (request.backgroundColor() != null) {
            update.set("background_color", request.backgroundColor());
        }
        if (request.isEnabled() != null) {
            update.set("is_enabled", request.isEnabled());
        }

        template.updateFirst(query, update, HomepageCard.class, CollectionName.HOMEPAGE_CARDS);
        return getCardById(server, id);
    }

    public boolean deleteCard(Server server, String id) {
        MongoTemplate template = getTemplate(server);
        Query query = Query.query(Criteria.where("_id").is(id));
        DeleteResult result = template.remove(query, HomepageCard.class, CollectionName.HOMEPAGE_CARDS);
        return result.getDeletedCount() > 0;
    }

    public void reorderCards(Server server, List<String> ids) {
        MongoTemplate template = getTemplate(server);

        for (int i = 0; i < ids.size(); i++) {
            Query query = Query.query(Criteria.where("_id").is(ids.get(i)));
            Update update = new Update().set("ordinal", i);
            template.updateFirst(query, update, HomepageCard.class, CollectionName.HOMEPAGE_CARDS);
        }
    }

    private int getMaxOrdinal(MongoTemplate template) {
        Query query = new Query().with(Sort.by(Sort.Direction.DESC, "ordinal")).limit(1);
        HomepageCard highest = template.findOne(query, HomepageCard.class, CollectionName.HOMEPAGE_CARDS);
        return highest != null ? highest.getOrdinal() : -1;
    }

    private MongoTemplate getTemplate(Server server) {
        return mongoProvider.getFromDatabaseName(server.getDatabaseName());
    }
}
