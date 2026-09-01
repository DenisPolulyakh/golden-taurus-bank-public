package ru.money.goldentaurusbank.www.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.money.goldentaurusbank.www.backend.model.domain.BudgetMonth;
import ru.money.goldentaurusbank.www.backend.model.domain.User;

import java.util.List;
import java.util.Optional;

@Repository
public interface BudgetMonthRepository extends JpaRepository<BudgetMonth, Long> {

    Optional<BudgetMonth> findByUserAndYearAndMonth(User user, Integer year, Integer month);

    List<BudgetMonth> findByUserAndYearOrderByMonthAsc(User user, Integer year);
}
