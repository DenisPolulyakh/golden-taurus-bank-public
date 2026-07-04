package ru.money.goldentaurusbank.www.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.money.goldentaurusbank.www.backend.model.domain.BankDictionary;
import ru.money.goldentaurusbank.www.backend.model.domain.User;

import java.util.List;
import java.util.Optional;

@Repository
public interface BankRepository extends JpaRepository<BankDictionary, Long> {

    Optional<BankDictionary> findByIdAndUser(Long id, User user);

    List<BankDictionary> findByUserOrderByNameAsc(User user);

    boolean existsByUserAndNameIgnoreCase(User user, String name);

    Optional<BankDictionary> findByUserAndNameIgnoreCase(User user, String name);

    boolean existsByIdAndUser(Long id, User user);

    void deleteByIdAndUser(Long id, User user);
}