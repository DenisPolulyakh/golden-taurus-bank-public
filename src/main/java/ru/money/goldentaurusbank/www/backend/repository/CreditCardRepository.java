package ru.money.goldentaurusbank.www.backend.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.money.goldentaurusbank.www.backend.model.domain.CreditCard;
import ru.money.goldentaurusbank.www.backend.model.domain.User;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface CreditCardRepository extends JpaRepository<CreditCard, Long> {

    /*
     * Поиска по номеру здесь нет и быть не может: шифр недетерминированный.
     * Всё, по чему можно искать, — название и открытые последние 4 цифры.
     */

    @EntityGraph(attributePaths = {"accumulators", "accumulators.bullionName", "accumulators.vault"})
    List<CreditCard> findByUserAndArchivedFalse(User user);

    @EntityGraph(attributePaths = {"accumulators", "accumulators.bullionName", "accumulators.vault"})
    @Query("SELECT c FROM CreditCard c WHERE c.user = :user AND c.archived = false "
            + "AND (LOWER(c.name) LIKE LOWER(CONCAT('%', :query, '%')) "
            + "OR c.last4 LIKE CONCAT('%', :query, '%'))")
    List<CreditCard> search(@Param("user") User user, @Param("query") String query);

    @EntityGraph(attributePaths = {"accumulators", "accumulators.bullionName", "accumulators.vault"})
    Optional<CreditCard> findByIdAndUserAndArchivedFalse(Long id, User user);

    /** Достаёт карту вместе с архивными — этим методом история читает удалённую. */
    Optional<CreditCard> findByIdAndUser(Long id, User user);

    int countByUserAndArchivedFalse(User user);

    @Query("SELECT COALESCE(SUM(c.debt), 0) FROM CreditCard c WHERE c.user = :user AND c.archived = false")
    BigDecimal getTotalDebt(@Param("user") User user);

    @Query("SELECT COALESCE(SUM(c.debt), 0) FROM CreditCard c WHERE c.user.id = :userId AND c.archived = false")
    BigDecimal getTotalDebtByUserId(@Param("userId") Long userId);
}
