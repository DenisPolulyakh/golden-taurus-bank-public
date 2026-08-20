package ru.money.goldentaurusbank.www.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.money.goldentaurusbank.www.backend.model.domain.Bullion;
import ru.money.goldentaurusbank.www.backend.model.domain.BullionName;
import ru.money.goldentaurusbank.www.backend.model.domain.User;
import ru.money.goldentaurusbank.www.backend.model.domain.Vault;
import ru.money.goldentaurusbank.www.backend.model.dto.enums.BullionType;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface BullionRepository extends JpaRepository<Bullion, Long> {

    /*
     * Выборки «что у пользователя есть сейчас» исключают архивные слитки.
     * Точечный поиск по id и по ключу (наименование + хранилище) их, наоборот,
     * находит: истории нужно описание слитка, а созданию — реактивация вместо
     * нарушения уникальности uk_bullions_user_bullion_name_vault.
     */

    List<Bullion> findByUserAndArchivedFalseOrderByCreatedAtDesc(User user);

    Optional<Bullion> findByIdAndUser(Long id, User user);

    Optional<Bullion> findByUserAndBullionNameIdAndVaultId(User user, Long bullionNameId, Long vaultId);

    Optional<Bullion> findByVaultAndBullionName(Vault vault, BullionName bullionName);


    boolean existsByBullionNameIdAndUserId(Long bullionNameId, Long userId);

    /** Кредитные слитки, ещё не занятые ни одной картой — из них собирается накопитель. */
    List<Bullion> findByUserAndBullionTypeAndArchivedFalseAndCreditCardIsNull(User user, BullionType bullionType);

    List<Bullion> findByCreditCardIdAndArchivedFalse(Long creditCardId);

    @Query("SELECT COALESCE(SUM(b.amount), 0) FROM Bullion b WHERE b.user = :user AND b.archived = false")
    BigDecimal getTotalAmountByUser(@Param("user") User user);

    @Query("SELECT COALESCE(SUM(b.amount), 0) FROM Bullion b WHERE b.user.id = :userId AND b.archived = false")
    BigDecimal getTotalAmountByUserId(@Param("userId") Long userId);

    @Query("SELECT COALESCE(SUM(b.amount), 0) FROM Bullion b "
            + "WHERE b.user = :user AND b.bullionName = :bullionName AND b.archived = false")
    BigDecimal getTotalAmountByUserAndBullionName(@Param("user") User user, @Param("bullionName") BullionName bullionName);
}
