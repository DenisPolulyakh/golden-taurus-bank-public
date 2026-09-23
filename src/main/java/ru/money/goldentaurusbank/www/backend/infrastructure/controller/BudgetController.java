package ru.money.goldentaurusbank.www.backend.infrastructure.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import ru.money.goldentaurusbank.www.backend.infrastructure.exception.ApplicationException;
import ru.money.goldentaurusbank.www.backend.model.domain.User;
import ru.money.goldentaurusbank.www.backend.model.dto.request.BudgetBucketRequest;
import ru.money.goldentaurusbank.www.backend.model.dto.request.BudgetCloseRequest;
import ru.money.goldentaurusbank.www.backend.model.dto.request.BudgetFundRequest;
import ru.money.goldentaurusbank.www.backend.model.dto.request.BudgetPlanRequest;
import ru.money.goldentaurusbank.www.backend.model.dto.request.BudgetSettingsRequest;
import ru.money.goldentaurusbank.www.backend.model.dto.statistic.BudgetMonthDto;
import ru.money.goldentaurusbank.www.backend.model.dto.statistic.BudgetYearDto;
import ru.money.goldentaurusbank.www.backend.model.dto.statistic.TransactionDto;
import ru.money.goldentaurusbank.www.backend.repository.UserRepository;
import ru.money.goldentaurusbank.www.backend.service.BudgetService;

import java.util.List;

import static ru.money.goldentaurusbank.www.backend.model.dto.enums.ResponseCodes.USER_NOT_FOUND;

/**
 * Экран «Отчёты» → вкладка «Бюджет на месяц».
 * <p>
 * Все изменяющие ручки отдают пересчитанный отчёт за месяц, а не пустой ответ:
 * иначе экран после каждой кнопки ходил бы за данными вторым запросом.
 */
@Slf4j
@RestController
@RequestMapping("/api/budget")
@RequiredArgsConstructor
public class BudgetController {

    private final BudgetService budgetService;
    private final UserRepository userRepository;

    @GetMapping("/settings")
    public ResponseEntity<BudgetMonthDto> getSettings(@AuthenticationPrincipal UserDetails userDetails) {
        User user = getUserFromUserDetails(userDetails);
        return ResponseEntity.ok(budgetService.getSettings(user));
    }

    @PutMapping("/settings")
    public ResponseEntity<BudgetMonthDto> saveSettings(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody BudgetSettingsRequest request) {

        User user = getUserFromUserDetails(userDetails);
        log.info("Save budget settings for user: {}", user.getId());
        return ResponseEntity.ok(budgetService.saveSettings(request, user));
    }

    @GetMapping("/{year}")
    public ResponseEntity<BudgetYearDto> getYear(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable int year) {

        User user = getUserFromUserDetails(userDetails);
        return ResponseEntity.ok(budgetService.getYear(year, user));
    }

    @GetMapping("/{year}/{month}")
    public ResponseEntity<BudgetMonthDto> getMonth(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable int year,
            @PathVariable int month) {

        User user = getUserFromUserDetails(userDetails);
        return ResponseEntity.ok(budgetService.getMonth(year, month, user));
    }

    /** Операции одного дня — раскрытая строка таблицы. */
    @GetMapping("/{year}/{month}/days/{day}/transactions")
    public ResponseEntity<List<TransactionDto>> getDayTransactions(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable int year,
            @PathVariable int month,
            @PathVariable int day) {

        User user = getUserFromUserDetails(userDetails);
        return ResponseEntity.ok(budgetService.getDayTransactions(year, month, day, user));
    }

    @PutMapping("/{year}/{month}/plan")
    public ResponseEntity<BudgetMonthDto> setPlan(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable int year,
            @PathVariable int month,
            @Valid @RequestBody BudgetPlanRequest request) {

        User user = getUserFromUserDetails(userDetails);
        log.info("Set budget plan {}-{} for user: {}", year, month, user.getId());
        return ResponseEntity.ok(budgetService.setPlan(year, month, request, user));
    }

    /** Пустое тело означает «добрать до плана»; сумма в теле — докинуть, в том числе сверх плана. */
    @PostMapping("/{year}/{month}/fund")
    public ResponseEntity<BudgetMonthDto> fund(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable int year,
            @PathVariable int month,
            @Valid @RequestBody(required = false) BudgetFundRequest request) {

        User user = getUserFromUserDetails(userDetails);
        log.info("Fund budget {}-{} for user: {}", year, month, user.getId());
        return ResponseEntity.ok(budgetService.fund(year, month,
                request == null ? new BudgetFundRequest() : request, user));
    }

    /** Без остатка на бюджетном слитке тело можно не слать — переносить нечего, но снимок пишется всё равно. */
    @PostMapping("/{year}/{month}/close")
    public ResponseEntity<BudgetMonthDto> closeMonth(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable int year,
            @PathVariable int month,
            @Valid @RequestBody(required = false) BudgetCloseRequest request) {

        User user = getUserFromUserDetails(userDetails);
        log.info("Close budget month {}-{} for user: {}", year, month, user.getId());
        return ResponseEntity.ok(budgetService.closeMonth(year, month,
                request == null ? new BudgetCloseRequest() : request, user));
    }

    @PostMapping("/{year}/{month}/reopen")
    public ResponseEntity<BudgetMonthDto> reopenMonth(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable int year,
            @PathVariable int month) {

        User user = getUserFromUserDetails(userDetails);
        log.info("Reopen budget month {}-{} for user: {}", year, month, user.getId());
        return ResponseEntity.ok(budgetService.reopenMonth(year, month, user));
    }

    @PostMapping("/{year}/{month}/transactions/{id}/rollback")
    public ResponseEntity<BudgetMonthDto> rollbackOperation(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable int year,
            @PathVariable int month,
            @PathVariable Long id) {

        User user = getUserFromUserDetails(userDetails);
        log.info("Rollback budget transaction {} for user: {}", id, user.getId());
        return ResponseEntity.ok(budgetService.rollbackOperation(id, year, month, user));
    }

    /** Переразметка операции: денег не двигает, меняет только корзину бюджета. */
    @PatchMapping("/transactions/{id}/bucket")
    public ResponseEntity<BudgetMonthDto> setBucket(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody BudgetBucketRequest request) {

        User user = getUserFromUserDetails(userDetails);
        log.info("Set budget bucket for transaction {}: {}", id, request.getBudgetOperation());
        return ResponseEntity.ok(budgetService.setBucket(id, request.getBudgetOperation(), user));
    }

    private User getUserFromUserDetails(UserDetails userDetails) {
        String email = userDetails.getUsername();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ApplicationException(
                        USER_NOT_FOUND.getCode(),
                        USER_NOT_FOUND.getMessage()
                ));
    }
}
