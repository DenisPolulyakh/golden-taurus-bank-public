package ru.money.goldentaurusbank.www.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.money.goldentaurusbank.www.backend.model.domain.TelegramLink;
import ru.money.goldentaurusbank.www.backend.model.domain.User;

import java.util.List;
import java.util.Optional;

public interface TelegramLinkRepository extends JpaRepository<TelegramLink, Long> {

    Optional<TelegramLink> findByChatId(Long chatId);

    @Query("SELECT l FROM TelegramLink l JOIN FETCH l.user WHERE l.chatId = :chatId")
    Optional<TelegramLink> findByChatIdWithUser(@Param("chatId") Long chatId);

    List<TelegramLink> findByUserOrderByLinkedAtAsc(User user);

    List<TelegramLink> findByUserId(Long userId);

    @Query("SELECT l FROM TelegramLink l JOIN FETCH l.user")
    List<TelegramLink> findAllWithUser();

    Optional<TelegramLink> findByIdAndUser(Long id, User user);
}
