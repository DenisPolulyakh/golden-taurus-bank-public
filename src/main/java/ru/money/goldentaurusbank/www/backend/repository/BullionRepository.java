package ru.money.goldentaurusbank.www.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.money.goldentaurusbank.www.backend.model.domain.Bullion;
import ru.money.goldentaurusbank.www.backend.model.domain.Category;
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


    Optional<Bullion> findByUserAndCategoryIdAndVaultId(User user, Long categoryId, Long vaultId);


    @Query("SELECT COALESCE(SUM(b.amount), 0) FROM Bullion b WHERE b.user = :user")
    BigDecimal getTotalAmountByUser(@Param("user") User user);


    @Query("SELECT COALESCE(SUM(b.amount), 0) FROM Bullion b WHERE b.user = :user and b.category = :category")
    BigDecimal getTotalAmountByUserAndCategory(@Param("user") User user, @Param("category") Category category);

    Optional<Bullion> findByVaultAndCategory(Vault vault, Category category);


    boolean existsByCategoryIdAndUserId(Long categoryId, Long userId);
}