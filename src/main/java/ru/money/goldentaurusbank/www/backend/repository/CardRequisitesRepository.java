package ru.money.goldentaurusbank.www.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.money.goldentaurusbank.www.backend.model.domain.CardRequisites;
import ru.money.goldentaurusbank.www.backend.model.domain.User;

import java.util.Optional;

public interface CardRequisitesRepository extends JpaRepository<CardRequisites, Long> {

    Optional<CardRequisites> findByUser(User user);
}
