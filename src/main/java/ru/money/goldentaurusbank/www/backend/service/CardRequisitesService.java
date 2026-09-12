package ru.money.goldentaurusbank.www.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.money.goldentaurusbank.www.backend.infrastructure.exception.ApplicationException;
import ru.money.goldentaurusbank.www.backend.model.domain.CardRequisites;
import ru.money.goldentaurusbank.www.backend.model.domain.User;
import ru.money.goldentaurusbank.www.backend.model.dto.request.CardRequisitesRequest;
import ru.money.goldentaurusbank.www.backend.model.dto.response.CardRequisitesResponse;
import ru.money.goldentaurusbank.www.backend.model.dto.response.EmergencyPackageResponse;
import ru.money.goldentaurusbank.www.backend.repository.CardRequisitesRepository;
import ru.money.goldentaurusbank.www.backend.repository.CreditCardRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import static ru.money.goldentaurusbank.www.backend.model.dto.enums.ResponseCodes.CARD_REQUISITES_CONFLICT;

@Service
@RequiredArgsConstructor
public class CardRequisitesService {

    private final CardRequisitesRepository cardRequisitesRepository;
    private final CreditCardRepository creditCardRepository;

    @Transactional(readOnly = true)
    public CardRequisitesResponse get(User user) {
        return cardRequisitesRepository.findByUser(user)
                .map(requisites -> new CardRequisitesResponse(requisites.getPayload(), requisites.getVersion()))
                .orElseGet(() -> new CardRequisitesResponse(null, null));
    }

    @Transactional
    public CardRequisitesResponse save(User user, CardRequisitesRequest request) {
        CardRequisites requisites = cardRequisitesRepository.findByUser(user).orElse(null);

        if (requisites == null) {
            if (request.getVersion() != null) {
                throw conflict();
            }
            requisites = CardRequisites.builder()
                    .user(user)
                    .payload(request.getPayload())
                    .build();
        } else {
            if (!Objects.equals(requisites.getVersion(), request.getVersion())) {
                throw conflict();
            }
            requisites.setPayload(request.getPayload());
        }

        CardRequisites saved = cardRequisitesRepository.saveAndFlush(requisites);
        return new CardRequisitesResponse(saved.getPayload(), saved.getVersion());
    }

    @Transactional(readOnly = true)
    public EmergencyPackageResponse buildEmergencyPackage(User user) {
        List<EmergencyPackageResponse.CardLine> cards = creditCardRepository.findByUserAndArchivedFalse(user)
                .stream()
                .map(card -> EmergencyPackageResponse.CardLine.builder()
                        .cardId(card.getId())
                        .name(card.getName())
                        .last4(card.getLast4())
                        .debt(card.getDebt())
                        .limit(card.getCardLimit())
                        .gracePeriodDate(card.getGracePeriodDate())
                        .build())
                .toList();

        return EmergencyPackageResponse.builder()
                .generatedAt(LocalDateTime.now())
                .cards(cards)
                .vault(get(user))
                .build();
    }

    private ApplicationException conflict() {
        return new ApplicationException(
                CARD_REQUISITES_CONFLICT.getCode(),
                CARD_REQUISITES_CONFLICT.getMessage()
        );
    }
}
