
package ru.money.goldentaurusbank.www.backend.infrastructure.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import ru.money.goldentaurusbank.www.backend.infrastructure.exception.ApplicationException;
import ru.money.goldentaurusbank.www.backend.model.domain.User;
import ru.money.goldentaurusbank.www.backend.model.dto.request.TransferRequest;
import ru.money.goldentaurusbank.www.backend.model.dto.response.SuccessResponse;
import ru.money.goldentaurusbank.www.backend.model.dto.statistic.DashboardDailyStatisticsDto;
import ru.money.goldentaurusbank.www.backend.model.dto.statistic.DashboardStatisticsDto;
import ru.money.goldentaurusbank.www.backend.model.dto.statistic.TransactionHistoryResponse;
import ru.money.goldentaurusbank.www.backend.model.dto.statistic.TransactionLogDto;
import ru.money.goldentaurusbank.www.backend.repository.UserRepository;
import ru.money.goldentaurusbank.www.backend.service.TransactionService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static ru.money.goldentaurusbank.www.backend.model.dto.enums.ResponseCodes.USER_NOT_FOUND;

@Slf4j
@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;
    private final UserRepository userRepository;  // Добавить

    /**
     * Получить статистику для дашборда
     */
    @GetMapping("/dashboard/statistics")
    public ResponseEntity<DashboardStatisticsDto> getDashboardStatistics(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) Integer year) {

        User user = getUserFromUserDetails(userDetails);  // Добавить
        log.info("Get dashboard statistics for user: {}, year: {}", user.getId(), year);
        if(year == null) {
            year = LocalDateTime.now().getYear();
        }
        DashboardStatisticsDto statistics = transactionService.getDashboardStatistics(user.getId(), year);
        return ResponseEntity.ok(statistics);
    }

    /**
     * Получить историю транзакций с пагинацией
     */
    @GetMapping("/history")
    public ResponseEntity<TransactionHistoryResponse> getTransactionHistory(
            @AuthenticationPrincipal UserDetails userDetails,  // Изменено
            @RequestParam(required = false) String operationType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        User user = getUserFromUserDetails(userDetails);  // Добавить
        log.info("Get transaction history for user: {}", user.getId());
        TransactionHistoryResponse response = transactionService.getTransactionHistory(
                user.getId(), operationType, status, fromDate, toDate, pageable);
        return ResponseEntity.ok(response);
    }

    /**
     * Получить цепочку операций для транзакции
     */
    @GetMapping("/{id}/chain")
    public ResponseEntity<List<TransactionLogDto>> getTransactionChain(
            @AuthenticationPrincipal UserDetails userDetails,  // Изменено
            @PathVariable Long id) {

        User user = getUserFromUserDetails(userDetails);  // Добавить
        log.info("Get transaction chain for user: {}, transaction: {}", user.getId(), id);
        List<TransactionLogDto> chain = transactionService.getTransactionChain(id);
        return ResponseEntity.ok(chain);
    }

    /**
     * Получить доступные года для фильтрации
     */
    @GetMapping("/available-years")
    public ResponseEntity<List<Integer>> getAvailableYears(
            @AuthenticationPrincipal UserDetails userDetails) {  // Изменено

        User user = getUserFromUserDetails(userDetails);  // Добавить
        log.info("Get available years for user: {}", user.getId());
        List<Integer> years = transactionService.getAvailableYears(user.getId());
        return ResponseEntity.ok(years);
    }

    /**
     * Откатить транзакцию
     */
    @PostMapping("/{id}/rollback")
    public ResponseEntity<Void> rollbackTransaction(
            @AuthenticationPrincipal UserDetails userDetails,  // Изменено
            @PathVariable Long id) {

        User user = getUserFromUserDetails(userDetails);  // Добавить
        log.info("Rollback transaction: {}, user: {}", id, user.getId());
        transactionService.rollbackTransaction(id);
        return ResponseEntity.ok().build();
    }

    /**
     * Откатить последнюю транзакцию пользователя
     */
    @PostMapping("/rollback-last")
    public ResponseEntity<Void> rollbackLastTransaction(
            @AuthenticationPrincipal UserDetails userDetails) {  // Изменено

        User user = getUserFromUserDetails(userDetails);  // Добавить
        log.info("Rollback last transaction for user: {}", user.getId());
        transactionService.rollbackLastTransaction(user.getId());
        return ResponseEntity.ok().build();
    }


    @PostMapping("/transfer")
    public ResponseEntity<SuccessResponse<Void>> transferAmount(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody TransferRequest request) {

        User user = getUserFromUserDetails(userDetails);
        transactionService.transferAmount(
                request.getFromBullionId(),
                request.getToBullionId(),
                request.getAmount(),
                user,
                request.getComment(),
                request.getDateOperation()
        );

        return ResponseEntity.ok(new SuccessResponse<>(
                0,
                "Перевод выполнен успешно",
                null
        ));
    }

    @GetMapping("/dashboard/daily-statistics")
    public ResponseEntity<DashboardDailyStatisticsDto> getDailyStatistics(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam int year,
            @RequestParam int month) {

        User user = getUserFromUserDetails(userDetails);
        DashboardDailyStatisticsDto statistics = transactionService.getDailyStatistics(user.getId(), year, month);
        return ResponseEntity.ok(statistics);
    }


    // Вспомогательный метод - такой же как в других контроллерах
    private User getUserFromUserDetails(UserDetails userDetails) {
        String email = userDetails.getUsername();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ApplicationException(
                        USER_NOT_FOUND.getCode(),
                        USER_NOT_FOUND.getMessage()
                ));
    }
}