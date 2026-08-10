package ru.money.goldentaurusbank.www.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.money.goldentaurusbank.www.backend.model.domain.BullionName;
import ru.money.goldentaurusbank.www.backend.model.domain.User;

import java.util.List;
import java.util.Optional;

@Repository
public interface BullionNameRepository extends JpaRepository<BullionName, Long> {

    List<BullionName> findByUserOrderByTitleAsc(User user);

    Optional<BullionName> findByIdAndUser(Long id, User user);

    boolean existsByIdAndUser(Long id, User user);

    @Query("SELECT COUNT(c) > 0 FROM BullionName c WHERE c.user = :user AND LOWER(c.title) = LOWER(:title)")
    boolean existsByUserAndTitleIgnoreCase(@Param("user") User user, @Param("title") String title);

    void deleteByIdAndUser(Long id, User user);

    Optional<BullionName> findByUserAndTitleIgnoreCase(User user, String title);

    @Query("SELECT c.color FROM BullionName c WHERE c.user = :user AND c.color IS NOT NULL")
    List<String> findUsedColorsByUser(@Param("user") User user);
}
