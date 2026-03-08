package gg.modl.backend.homepage.service;

import gg.modl.backend.database.mongo.repository.HomepageCardMongoRepository;
import gg.modl.backend.homepage.data.HomepageCard;
import gg.modl.backend.homepage.dto.request.CreateCardRequest;
import gg.modl.backend.homepage.dto.request.UpdateCardRequest;
import gg.modl.backend.server.data.Server;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class HomepageCardService {
    private final HomepageCardMongoRepository homepageCardRepository;

    public List<HomepageCard> getAllCards(Server server) {
        return homepageCardRepository.findAllOrdered(server);
    }

    public List<HomepageCard> getVisibleCards(Server server) {
        return homepageCardRepository.findVisibleOrdered(server);
    }

    public Optional<HomepageCard> getCardById(Server server, String id) {
        return homepageCardRepository.findByCardId(server, id);
    }

    public HomepageCard createCard(Server server, CreateCardRequest request) {
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
                .ordinal(homepageCardRepository.findMaxOrdinal(server) + 1)
                .isEnabled(request.isEnabled() != null ? request.isEnabled() : true)
                .createdAt(new Date())
                .updatedAt(new Date())
                .build();

        return homepageCardRepository.saveEntity(server, card);
    }

    public Optional<HomepageCard> updateCard(Server server, String id, UpdateCardRequest request) {
        return homepageCardRepository.updateCard(
                server,
                id,
                request.title(),
                request.description(),
                request.icon(),
                request.iconColor(),
                request.actionType(),
                request.actionUrl(),
                request.actionButtonText(),
                request.categoryId(),
                request.backgroundColor(),
                request.isEnabled(),
                new Date()
        );
    }

    public boolean deleteCard(Server server, String id) {
        return homepageCardRepository.deleteByCardId(server, id);
    }

    public void reorderCards(Server server, List<String> ids) {
        homepageCardRepository.reorderCards(server, ids);
    }
}
