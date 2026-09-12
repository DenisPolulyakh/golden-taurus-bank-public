package ru.money.goldentaurusbank.www.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.money.goldentaurusbank.www.backend.model.domain.TelegramLinkCode;

import java.util.Optional;

public interface TelegramLinkCodeRepository extends JpaRepository<TelegramLinkCode, Long> {

    @Query("SELECT c FROM TelegramLinkCode c JOIN FETCH c.user WHERE c.code = :code")
    Optional<TelegramLinkCode> findByCode(@Param("code") String code);
}
