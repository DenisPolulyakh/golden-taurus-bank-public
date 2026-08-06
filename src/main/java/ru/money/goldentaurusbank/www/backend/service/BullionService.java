package ru.money.goldentaurusbank.www.backend.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.money.goldentaurusbank.www.backend.infrastructure.exception.ApplicationException;
import ru.money.goldentaurusbank.www.backend.model.domain.Bullion;
import ru.money.goldentaurusbank.www.backend.model.domain.BullionName;
import ru.money.goldentaurusbank.www.backend.model.domain.User;
import ru.money.goldentaurusbank.www.backend.model.domain.Vault;
import ru.money.goldentaurusbank.www.backend.model.dto.enums.AccountType;
import ru.money.goldentaurusbank.www.backend.model.dto.request.*;
import ru.money.goldentaurusbank.www.backend.model.dto.response.BullionResponse;
import ru.money.goldentaurusbank.www.backend.model.dto.response.GroupedBullionResponse;
import ru.money.goldentaurusbank.www.backend.model.mapper.BullionMapper;
import ru.money.goldentaurusbank.www.backend.model.mapper.BullionRequestMapper;
import ru.money.goldentaurusbank.www.backend.repository.BullionRepository;
import ru.money.goldentaurusbank.www.backend.repository.BullionNameRepository;
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

        if (existingBullion.isPresent()) {
            RefillBullionRequest refillBullionRequest = bullionRequestMapper.toRefillBullionRequest(request, REFILL_EXISTS_BULLION_COMMENT);
            return refillBullion(user, refillBullionRequest);
        }

        Bullion bullion = Bullion.builder()
                .bullionName(bullionName)
                .vault(vault)
                .amount(BigDecimal.ZERO)
                .description(request.getDescription())
                .user(user)
                .build();

        bullionRepository.save(bullion);
        RefillBullionRequest refillBullionRequest = bullionRequestMapper.toRefillBullionRequest(request, CREATE_FIRST_BULLION_COMMENT);

        return refillBullion(user, refillBullionRequest);
    }

    @Transactional(readOnly = true)
    public List<BullionResponse> getAllBullions(User user) {
        return bullionMapper.toResponseList(bullionRepository.findByUserOrderByCreatedAtDesc(user));
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

        if (request.getAmount() != null) {
            bullion.setAmount(request.getAmount());
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

        // If bullion has nonzero amount, treat its deletion as a withdrawal of the full amount
        if (bullion.getAmount().compareTo(BigDecimal.ZERO) != 0) {
            WithdrawBullionRequest request = new WithdrawBullionRequest();
            request.setAmount(bullion.getAmount());
            request.setBullionNameId(bullion.getBullionName().getId());
            request.setVaultId(bullion.getVault().getId());
            request.setDateOperation(LocalDateTime.now());
            request.setUserComment("Удаление слитка");
            transactionService.withdrawBullion(request, user, null);
            // After withdrawal, bullion amount should be zero; flush to ensure state
            bullionRepository.flush();
        }

        bullionRepository.deleteByIdAndUser(bullionId, user);
    }

    @Transactional(readOnly = true)
    public GroupedBullionResponse getGroupedBullions(User user) {
        List<Bullion> bullions = bullionRepository.findByUserOrderByCreatedAtDesc(user);

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
                        boolean allowed = calculateAllowed(vault);

                        return GroupedBullionResponse.BullionNameBullion.VaultInfo.builder()
                            .id(b.getVault().getId())
                            .name(b.getVault().getName())
                            .amount(b.getAmount())
                            .accountType(vault.getAccountType().name())
                            .closeDate(vault.getCloseDate())
                            .allowedIncome(allowed)
                            .allowedDelete(allowed)
                            .allowedExpense(allowed)
                            .allowedTransfer(allowed)
                            .build();})
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
        List<Bullion> bullions = bullionRepository.findByUserOrderByCreatedAtDesc(user);

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
        if (request.isToLiquidityVault()) {
            Vault liquidityVault = vaultService.getLiquidityReserve(user);
            transactionService.transferBullion(bullion.getId(), liquidityVault.getId(), user,request.getDateOperation());
            log.info("Bullion {} transfer to liquidity reverse id = {}", bullion.getBullionName().getTitle(), liquidityVault.getId());
        } else {
            transactionService.transferBullion(bullion.getId(), request.getToVaultId(), user, request.getDateOperation());
            log.info("Bullion {} transfer to vault id = {}", bullion.getBullionName().getTitle(), request.getToVaultId());
        }
        bullionRepository.deleteByIdAndUser(bullionId, user);
        log.info("Bullion {} deleted", bullionId);
    }

    private boolean calculateAllowed(Vault vault) {
        if (vault == null) {
            return true;
        }
        if (vault.getAccountType() == null || !AccountType.TERM.equals(vault.getAccountType())) {
            return true;
        }
        if (vault.getCloseDate() == null) {
            return true;
        }
        LocalDate today = LocalDate.now();
        LocalDate closeDate = vault.getCloseDate();
        return today.isEqual(closeDate) || today.isAfter(closeDate);
    }
}