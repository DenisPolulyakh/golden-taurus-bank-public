package ru.money.goldentaurusbank.www.backend.service;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.money.goldentaurusbank.www.backend.infrastructure.exception.ApplicationException;
import ru.money.goldentaurusbank.www.backend.model.domain.Bullion;
import ru.money.goldentaurusbank.www.backend.model.domain.BullionName;
import ru.money.goldentaurusbank.www.backend.model.domain.User;
import ru.money.goldentaurusbank.www.backend.model.domain.Vault;
import ru.money.goldentaurusbank.www.backend.model.dto.request.*;
import ru.money.goldentaurusbank.www.backend.model.dto.response.BullionResponse;
import ru.money.goldentaurusbank.www.backend.model.dto.response.GroupedBullionResponse;
import ru.money.goldentaurusbank.www.backend.model.mapper.BullionMapper;
import ru.money.goldentaurusbank.www.backend.model.mapper.BullionRequestMapper;
import ru.money.goldentaurusbank.www.backend.repository.BullionNameRepository;
import ru.money.goldentaurusbank.www.backend.repository.BullionRepository;
import ru.money.goldentaurusbank.www.backend.repository.VaultRepository;
import ru.money.goldentaurusbank.www.backend.util.math.FinancialCalculator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static ru.money.goldentaurusbank.www.backend.model.dto.enums.ResponseCodes.*;
import static ru.money.goldentaurusbank.www.backend.util.math.FinancialCalculator.calculateWeightedAverageRateWithDecimal;

@Service
@RequiredArgsConstructor
public class BullionService {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal(100);
    private static final String CREATE_FIRST_BULLION_COMMENT = "Первоначальное создание слитка";
    private static final String REFILL_EXISTS_BULLION_COMMENT = "Пополнение существующего слитка";
    // public — на эти тексты смотрят тесты корректировки суммы
    public static final String DEPOSIT_AMOUNT_COMMENT = "Пополнение при корректировке суммы слитка";
    public static final String WITHDRAWAL_AMOUNT_COMMENT = "Снятие при корректировке суммы слитка";
    private static final String DELETE_BULLION_COMMENT = "Удаление слитка";
    private static final Logger log = LoggerFactory.getLogger(BullionService.class);

    private final BullionRepository bullionRepository;
    private final BullionNameRepository bullionNameRepository;
    private final VaultRepository vaultRepository;
    private final VaultService vaultService;
    private final BullionMapper bullionMapper;
    private final TransactionService transactionService;
    private final BullionRequestMapper bullionRequestMapper;


    @Transactional
    public BullionResponse refillBullion(User user, RefillBullionRequest request) {
        Bullion refilledBullion = transactionService.refillBullion(request, user, null);
        return bullionMapper.toResponse(refilledBullion);
    }


    @Transactional
    public BullionResponse withDrawBullion(User user, WithdrawBullionRequest request) {
        Bullion withdrawBullion = transactionService.withdrawBullion(request, user, null);
        return bullionMapper.toResponse(withdrawBullion);
    }


    @Transactional
    public BullionResponse transferAmountBullion(User user, TransferRequest request) {
        Bullion withdrawBullion = transactionService.transferAmount(request, user);
        log.info("Сумма перенесена из слитка {} в слиток {}", request.getFromBullionId(), request.getToBullionId());
        return bullionMapper.toResponse(withdrawBullion);

    }

    @Transactional
    public BullionResponse createBullion(User user, BullionRequest request) {
        BullionName bullionName = bullionNameRepository.findByIdAndUser(request.getBullionNameId(), user)
                .orElseThrow(() -> new ApplicationException(
                        BULLION_NAME_NOT_FOUND.getCode(),
                        BULLION_NAME_NOT_FOUND.getMessage()
                ));

        Vault vault = vaultRepository.findByIdAndUser(request.getVaultId(), user)
                .orElseThrow(() -> new ApplicationException(
                        VAULT_NOT_FOUND.getCode(),
                        VAULT_NOT_FOUND.getMessage()
                ));

        Optional<Bullion> existingBullion = bullionRepository.findByUserAndBullionNameIdAndVaultId(
                user, request.getBullionNameId(), request.getVaultId());

        if (existingBullion.isPresent() && !existingBullion.get().isArchived()) {
            RefillBullionRequest refillBullionRequest = bullionRequestMapper.toRefillBullionRequest(request, determineComment(request.getUserComment(), REFILL_EXISTS_BULLION_COMMENT));
            return refillBullion(user, refillBullionRequest);
        }

        // Архивный слиток возвращается к жизни вместе со своей историей: пара
        // «наименование + хранилище» уникальна, второй такой слиток не завести.
        Bullion bullion = existingBullion.orElseGet(() -> Bullion.builder()
                .bullionName(bullionName)
                .vault(vault)
                .amount(BigDecimal.ZERO)
                .user(user)
                .build());
        bullion.setArchived(false);
        bullion.setDescription(request.getDescription());
        bullionRepository.save(bullion);

        // Стартовый остаток не идёт в «Доход за месяц» — иначе месяц создания слитка
        // показал бы доход на всю сумму уже накопленного.
        if (request.getAmount() != null && request.getAmount().compareTo(BigDecimal.ZERO) > 0) {
            transactionService.openingBalance(bullion.getId(), request.getAmount(), user,
                    determineComment(request.getUserComment(), CREATE_FIRST_BULLION_COMMENT), request.getDateOperation());
        }

        return bullionMapper.toResponse(bullion);
    }

    @Transactional(readOnly = true)
    public List<BullionResponse> getAllBullions(User user) {
        return bullionMapper.toResponseList(bullionRepository.findByUserAndArchivedFalseOrderByCreatedAtDesc(user));
    }

    @Transactional(readOnly = true)
    public BullionResponse getBullionById(User user, Long bullionId) {
        Bullion bullion = bullionRepository.findByIdAndUser(bullionId, user)
                .orElseThrow(() -> new ApplicationException(
                        BULLION_NOT_FOUND.getCode(),
                        BULLION_NOT_FOUND.getMessage()
                ));
        return bullionMapper.toResponse(bullion);
    }

    @Transactional
    public BullionResponse updateBullion(User user, Long bullionId, BullionRequest request) {
        Bullion bullion = bullionRepository.findByIdAndUser(bullionId, user)
                .orElseThrow(() -> new ApplicationException(
                        BULLION_NOT_FOUND.getCode(),
                        BULLION_NOT_FOUND.getMessage()
                ));

        // Ручная правка суммы оформляется корректирующей транзакцией на дельту:
        // иначе остаток разъезжается с графиком, который считается по операциям.
        if (request.getAmount() != null) {
            BigDecimal delta = request.getAmount().subtract(bullion.getAmount());
            if (delta.compareTo(BigDecimal.ZERO) > 0) {
                transactionService.deposit(bullionId, delta, user, determineComment(request.getUserComment(), DEPOSIT_AMOUNT_COMMENT), request.getDateOperation(), null);
            } else if (delta.compareTo(BigDecimal.ZERO) < 0) {
                transactionService.withdraw(bullionId, delta.negate(), user, determineComment(request.getUserComment(), WITHDRAWAL_AMOUNT_COMMENT), request.getDateOperation(), null);
            }
        }

        bullion.setDescription(request.getDescription());

        if (request.getBullionNameId() != null && !bullion.getBullionName().getId().equals(request.getBullionNameId())) {
            BullionName bullionName = bullionNameRepository.findByIdAndUser(request.getBullionNameId(), user)
                    .orElseThrow(() -> new ApplicationException(
                            BULLION_NAME_NOT_FOUND.getCode(),
                            BULLION_NAME_NOT_FOUND.getMessage()
                    ));
            bullion.setBullionName(bullionName);
        }

        if (request.getVaultId() != null && !bullion.getVault().getId().equals(request.getVaultId())) {
            Vault vault = vaultRepository.findByIdAndUser(request.getVaultId(), user)
                    .orElseThrow(() -> new ApplicationException(
                            VAULT_NOT_FOUND.getCode(),
                            VAULT_NOT_FOUND.getMessage()
                    ));
            bullion.setVault(vault);
        }

        bullionRepository.save(bullion);

        return bullionMapper.toResponse(bullion);
    }

    @Transactional
    public void deleteBullion(User user, Long bullionId) {
        Bullion bullion = bullionRepository.findByIdAndUser(bullionId, user)
                .orElseThrow(() -> new ApplicationException(
                        BULLION_NOT_FOUND.getCode(),
                        BULLION_NOT_FOUND.getMessage()
                ));

        // Остаток уходит снятием, сам слиток архивируется: физически удалить его нельзя,
        // на него ссылается история операций.
        if (bullion.getAmount().compareTo(BigDecimal.ZERO) > 0) {
            transactionService.withdraw(bullionId, bullion.getAmount(), user, DELETE_BULLION_COMMENT, null, null);
        }

        transactionService.archive(bullion);
    }

    @Transactional(readOnly = true)
    public GroupedBullionResponse getGroupedBullions(User user) {
        List<Bullion> bullions = bullionRepository.findByUserAndArchivedFalseOrderByCreatedAtDesc(user);

        Map<Long, List<Bullion>> groupedByBullionName = bullions.stream()
                .collect(Collectors.groupingBy(b -> b.getBullionName().getId()));

        List<Vault> vaults = vaultRepository.findByUser(user);


        FinancialCalculator.AverageData averageDataVault = new FinancialCalculator.AverageData(BigDecimal.ZERO, BigDecimal.ZERO);
        FinancialCalculator.AverageData averageDataBullionName = new FinancialCalculator.AverageData(BigDecimal.ZERO, BigDecimal.ZERO);

        averageDataVault = calculateWeightedAverageRateWithDecimal(vaults, v -> v.getBullions().stream()
                .map(Bullion::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add), Vault::getInterestRate);


        List<GroupedBullionResponse.BullionNameBullion> bullionsList = new ArrayList<>();
        for (Map.Entry<Long, List<Bullion>> entry : groupedByBullionName.entrySet()) {
            Long bullionNameId = entry.getKey();
            List<Bullion> bullionNameBullions = entry.getValue();

            String bullionNameTitle = bullionNameBullions.get(0).getBullionName().getTitle();
            String bullionNameColor = bullionNameBullions.get(0).getBullionName().getColor();
            BigDecimal totalBullionNameAmount = bullionNameBullions.stream()
                    .map(Bullion::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            averageDataBullionName = calculateWeightedAverageRateWithDecimal(bullionNameBullions, Bullion::getAmount, b -> b.getVault().getInterestRate());


            List<GroupedBullionResponse.BullionNameBullion.VaultInfo> vaultInfos = bullionNameBullions.stream()
                    .map(b -> {

                        Vault vault = b.getVault();

                        return GroupedBullionResponse.BullionNameBullion.VaultInfo.builder()
                                .id(b.getVault().getId())
                                .name(b.getVault().getName())
                                .amount(b.getAmount())
                                .accountType(vault.getAccountType().name())
                                .closeDate(vault.getCloseDate())
                                // Операции разрешают только галочки хранилища,
                                // те же правила, что в VaultMapper.enrichVaultResponse
                                .allowedIncome(vault.isAllowedIncome())
                                .allowedExpense(vault.isAllowedExpense())
                                .allowedTransfer(vault.isAllowedTransfer())
                                .allowedDelete(true)
                                .allowedChangeAmount(true)
                                .allowedEdit(true)
                                .build();
                    })
                    .collect(Collectors.toList());


            bullionsList.add(GroupedBullionResponse.BullionNameBullion.builder()
                    .bullionNameId(bullionNameId)
                    .bullionNameTitle(bullionNameTitle)
                    .bullionNameColor(bullionNameColor)
                    .bullionNameAmount(averageDataBullionName.totalAmount())
                    .bullionNameAverageRate(averageDataBullionName.avgRate())
                    .vaults(vaultInfos)
                    .build());
        }

        bullionsList.sort((a, b) -> b.getBullionNameAmount().compareTo(a.getBullionNameAmount()));

        GroupedBullionResponse response = GroupedBullionResponse.builder()
                .totalAmount(averageDataVault.totalAmount())
                .averageRate(averageDataVault.avgRate())
                .bullionNameBullionList(bullionsList)
                .countVaults(vaults.size())
                .countBullions(bullionsList.size()).build();

        return response;
    }


    @Transactional(readOnly = true)
    public BigDecimal getTotalAmount(User user) {
        return bullionRepository.getTotalAmountByUser(user);
    }

    @Transactional(readOnly = true)
    public BigDecimal getTotalAmountByBullionName(User user, BullionName bullionName) {
        return bullionRepository.getTotalAmountByUserAndBullionName(user, bullionName);
    }

    @Transactional(readOnly = true)
    public BigDecimal getAverageRate(User user) {
        List<Bullion> bullions = bullionRepository.findByUserAndArchivedFalseOrderByCreatedAtDesc(user);

        if (bullions.isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal weightedRateSum = BigDecimal.ZERO;
        BigDecimal totalWeight = BigDecimal.ZERO;

        for (Bullion bullion : bullions) {
            BigDecimal amount = bullion.getAmount();
            BigDecimal rate = bullion.getVault().getInterestRate();
            weightedRateSum = weightedRateSum.add(amount.multiply(rate));
            totalWeight = totalWeight.add(amount);
        }

        if (totalWeight.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        return weightedRateSum.divide(totalWeight, 2, RoundingMode.HALF_UP);
    }

    @Transactional
    public void deleteBullionWithTransfer(User user, Long bullionId, DeleteBullionWithTransferRequest request) {
        if (request.getToVaultId() == null && !request.isToLiquidityVault()) {
            throw new ApplicationException(VAULT_NOT_SET.getCode(), VAULT_NOT_SET.getMessage());
        }
        Bullion bullion = bullionRepository.findByIdAndUser(bullionId, user)
                .orElseThrow(() -> new ApplicationException(
                        BULLION_NOT_FOUND.getCode(),
                        BULLION_NOT_FOUND.getMessage()
                ));
        Long toVaultId = request.isToLiquidityVault()
                ? vaultService.getLiquidityReserve(user).getId()
                : request.getToVaultId();

        // transferBullion сам архивирует исходный слиток после перевода остатка.
        transactionService.transferBullion(bullion.getId(), toVaultId, user, null, atStartOfDay(request.getDateOperation()));
        log.info("Bullion {} transferred to vault id = {} and archived", bullion.getBullionName().getTitle(), toVaultId);
    }

    private static LocalDateTime atStartOfDay(LocalDate date) {
        return date == null ? null : date.atStartOfDay();
    }

    private String determineComment(String userComment, String defaultComment) {
        return StringUtils.isNotBlank(userComment) ? userComment.trim() : defaultComment;
    }
}
