package ru.money.goldentaurusbank.www.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.money.goldentaurusbank.www.backend.model.domain.Bullion;
import ru.money.goldentaurusbank.www.backend.model.domain.BullionName;
import ru.money.goldentaurusbank.www.backend.model.domain.User;
import ru.money.goldentaurusbank.www.backend.model.domain.Vault;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface BullionRepository extends JpaRepository<Bullion, Long> {

    List<Bullion> findByUserOrderByCreatedAtDesc(User user);

    Optional<Bullion> findByIdAndUser(Long id, User user);

    boolean existsByIdAndUser(Long id, User user);

    void deleteByIdAndUser(Long id, User user);


    Optional<Bullion> findByUserAndBullionNameIdAndVaultId(User user, Long bullionNameId, Long vaultId);


    @Query("SELECT COALESCE(SUM(b.amount), 0) FROM Bullion b WHERE b.user = :user")
    BigDecimal getTotalAmountByUser(@Param("user") User user);


    @Query("SELECT COALESCE(SUM(b.amount), 0) FROM Bullion b WHERE b.user = :user and b.bullionName = :bullionName")
    BigDecimal getTotalAmountByUserAndBullionName(@Param("user") User user, @Param("bullionName") BullionName bullionName);

    Optional<Bullion> findByVaultAndBullionName(Vault vault, BullionName bullionName);


    boolean existsByBullionNameIdAndUserId(Long bullionNameId, Long userId);
    @Query("SELECT COALESCE(SUM(b.amount), 0) FROM Bullion b WHERE b.user.id = :userId")
    BigDecimal getTotalAmountByUserId(@Param("userId") Long userId);
}