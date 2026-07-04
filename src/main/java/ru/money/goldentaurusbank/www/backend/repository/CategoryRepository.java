package ru.money.goldentaurusbank.www.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.money.goldentaurusbank.www.backend.model.domain.Category;
import ru.money.goldentaurusbank.www.backend.model.domain.User;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {
    
    List<Category> findByUserOrderByNameAsc(User user);
    
    Optional<Category> findByIdAndUser(Long id, User user);
    
    boolean existsByIdAndUser(Long id, User user);
    
    @Query("SELECT COUNT(c) > 0 FROM Category c WHERE c.user = :user AND LOWER(c.name) = LOWER(:name)")
    boolean existsByUserAndNameIgnoreCase(@Param("user") User user, @Param("name") String name);
    
    void deleteByIdAndUser(Long id, User user);

    Optional<Category> findByUserAndNameIgnoreCase(User user, String name);

    @Query("SELECT c.color FROM Category c WHERE c.user = :user AND c.color IS NOT NULL")
    List<String> findUsedColorsByUser(@Param("user") User user);
}