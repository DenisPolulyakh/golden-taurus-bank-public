package ru.money.goldentaurusbank.www.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.money.goldentaurusbank.www.backend.infrastructure.exception.ApplicationException;
import ru.money.goldentaurusbank.www.backend.model.domain.BankDictionary;
import ru.money.goldentaurusbank.www.backend.model.domain.Bullion;
import ru.money.goldentaurusbank.www.backend.model.domain.User;
import ru.money.goldentaurusbank.www.backend.model.domain.Vault;
import ru.money.goldentaurusbank.www.backend.model.dto.enums.AccountType;
import ru.money.goldentaurusbank.www.backend.model.dto.enums.VaultType;
import ru.money.goldentaurusbank.www.backend.model.dto.request.VaultRequest;
import ru.money.goldentaurusbank.www.backend.model.dto.response.PageResponse;
import ru.money.goldentaurusbank.www.backend.model.dto.response.VaultResponse;
import ru.money.goldentaurusbank.www.backend.model.dto.response.VaultSummaryResponse;
import ru.money.goldentaurusbank.www.backend.model.mapper.VaultMapper;
import ru.money.goldentaurusbank.www.backend.repository.BankRepository;
import ru.money.goldentaurusbank.www.backend.repository.VaultRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static ru.money.goldentaurusbank.www.backend.model.dto.enums.ResponseCodes.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class VaultService {

    private static final String NAME_LIQUIDITY_RESERVE = "Ликвидный резерв";
    private static final String DESCRIPTION_LIQUIDITY_RESERVE = "Средства для распределения по другим хранилищам или купите себе что-нибудь, порадуйте себя в конце концов!";

    private final VaultRepository vaultRepository;
    private final BankRepository bankRepository;
    private final VaultMapper vaultMapper;
    private final TransactionService transactionService;

    @Transactional
    public Vault createVault(User user, VaultRequest request) {
        String name = request.getName().trim();

        if (!vaultRepository.findByUserAndNameIgnoreCaseAndBankId(user, name, request.getBankId()).isEmpty()) {
            throw new ApplicationException(
                    VAULT_ALREADY_EXISTS.getCode(),
                    "Хранилище с названием \"" + name + "\" уже существует"
            );
        }

        Vault.VaultBuilder builder = Vault.builder()
                .name(name)
                .interestRate(request.getInterestRate() != null ? request.getInterestRate() : BigDecimal.ZERO)
                .description(request.getDescription() != null ? request.getDescription().trim() : null)
                .vaultType(request.getVaultType() != null ? request.getVaultType() : VaultType.REGULAR)
                .accountType(request.getAccountType() != null ? request.getAccountType() : AccountType.SAVINGS)
                .closeDate(request.getCloseDate())
                .allowedIncome(request.getAllowedIncome() == null || request.getAllowedIncome())
                .allowedExpense(request.getAllowedExpense() == null || request.getAllowedExpense())
                .allowedTransfer(request.getAllowedTransfer() == null || request.getAllowedTransfer())
                .user(user);

        // Если указан bankId - привязываем банк
        if (request.getBankId() != null) {
            BankDictionary bank = bankRepository.findByIdAndUser(request.getBankId(), user)
                    .orElseThrow(() -> new ApplicationException(
                            BANK_NOT_FOUND.getCode(),
                            BANK_NOT_FOUND.getMessage()
                    ));
            builder.bank(bank);
        }

        Vault vault = builder.build();
        vaultRepository.save(vault);
        return vault;
    }

    public VaultResponse getVaultByIdAndUser(User user, Long vaultId) {
        Vault vault = vaultRepository.findByIdAndUserAndArchivedFalse(vaultId, user)
                .orElseThrow(() -> new ApplicationException(
                        VAULT_NOT_FOUND.getCode(),
                        VAULT_NOT_FOUND.getMessage()
                ));
        return vaultMapper.toResponse(vault);
    }


    @Transactional
    public VaultResponse createVaultAndGetResponse(User user, VaultRequest request) {
        return vaultMapper.toResponse(createVault(user, request));
    }

    @Transactional
    public VaultResponse updateVaultAndGetResponse(User user, Long vaultId, VaultRequest request) {
        Vault vault = vaultRepository.findByIdAndUserAndArchivedFalse(vaultId, user)
                .orElseThrow(() -> new ApplicationException(
                        VAULT_NOT_FOUND.getCode(),
                        VAULT_NOT_FOUND.getMessage()
                ));

        String newName = request.getName().trim();

        boolean duplicate = vaultRepository.findByUserAndNameIgnoreCaseAndBankId(user, newName, request.getBankId())
                .stream()
                .anyMatch(existing -> !existing.getId().equals(vaultId));
        if (duplicate) {
            throw new ApplicationException(
                    VAULT_ALREADY_EXISTS.getCode(),
                    "Хранилище с названием \"" + newName + "\" уже существует"
            );
        }

        vault.setName(newName);
        vault.setInterestRate(request.getInterestRate() != null ? request.getInterestRate() : BigDecimal.ZERO);
        vault.setDescription(request.getDescription() != null ? request.getDescription().trim() : null);
        vault.setVaultType(request.getVaultType() != null ? request.getVaultType() : vault.getVaultType());
        vault.setAccountType(request.getAccountType() != null ? request.getAccountType() : vault.getAccountType());
        vault.setCloseDate(request.getCloseDate());
        vault.setAllowedIncome(request.getAllowedIncome() != null ? request.getAllowedIncome() : vault.isAllowedIncome());
        vault.setAllowedExpense(request.getAllowedExpense() != null ? request.getAllowedExpense() : vault.isAllowedExpense());
        vault.setAllowedTransfer(request.getAllowedTransfer() != null ? request.getAllowedTransfer() : vault.isAllowedTransfer());


        // Обновляем банк
        if (request.getBankId() != null) {
            BankDictionary bank = bankRepository.findByIdAndUser(request.getBankId(), user)
                    .orElseThrow(() -> new ApplicationException(
                            BANK_NOT_FOUND.getCode(),
                            BANK_NOT_FOUND.getMessage()
                    ));
            vault.setBank(bank);
        } else {
            vault.setBank(null);
        }

        vaultRepository.save(vault);
        return vaultMapper.toResponse(vault);
    }


    @Transactional
    public void deleteVault(User user, Long vaultId) {
        Vault vault = vaultRepository.findByIdAndUserAndArchivedFalse(vaultId, user)
                .orElseThrow(() -> new ApplicationException(
                        VAULT_NOT_FOUND.getCode(),
                        VAULT_NOT_FOUND.getMessage()
                ));

        List<Bullion> activeBullions = List.copyOf(vault.getBullions());
        long fundedCount = activeBullions.stream().filter(this::hasAmount).count();
        boolean hasFunded = fundedCount > 0;

        if (vault.getVaultType() == VaultType.LIQUIDITY_BUFFER && hasFunded) {
            throw new ApplicationException(
                    LIQUIDITY_RESERVE_NOT_EMPTY.getCode(),
                    LIQUIDITY_RESERVE_NOT_EMPTY.getMessage()
            );
        }

        Vault liquidityReserve = hasFunded ? getLiquidityReserve(user) : null;
        Long batchId = hasFunded ? transactionService.createBatchId() : null;

        for (Bullion bullion : activeBullions) {
            if (hasAmount(bullion)) {
                transactionService.transferBullion(bullion.getId(), liquidityReserve.getId(), user, batchId, LocalDateTime.now());
            } else {
                transactionService.archive(bullion);
            }
        }

        // Хранилище остаётся в БД под флагом: на него ссылаются архивные слитки,
        // а через них — история операций.
        vault.setArchived(true);
        vaultRepository.save(vault);
        log.info("[VaultService.deleteVault] vault id = {} archived, bullions moved to reserve = {}", vaultId, fundedCount);
    }

    private boolean hasAmount(Bullion bullion) {
        return bullion.getAmount() != null && bullion.getAmount().compareTo(BigDecimal.ZERO) > 0;
    }

    @Transactional(readOnly = true)
    public PageResponse<VaultResponse> getAllVaults(User user, String search, String sortBy, String sortOrder, int page, int size) {

        int pageNumber = Math.max(page - 1, 0);
        int pageSize = size > 0 ? Math.min(size, 100) : 10;

        Sort.Direction direction = Sort.Direction.ASC;
        if (sortOrder != null && sortOrder.equalsIgnoreCase("desc")) {
            direction = Sort.Direction.DESC;
        }


        String sortField = "name";
        if (sortBy != null) {
            switch (sortBy) {
                case "interestRate":
                    sortField = "interestRate";
                    break;
                case "bankName":
                    sortField = "bank.name";
                    break;
                case "totalAmount":
                    sortField = null;
                    break;
                default:
                    sortField = "name";
                    break;
            }
        }

        Pageable pageable = sortField != null
                ? PageRequest.of(pageNumber, pageSize, Sort.by(direction, sortField))
                : PageRequest.of(pageNumber, pageSize);

        Page<Vault> vaultPage;

        if (search != null && !search.trim().isEmpty()) {
            vaultPage = vaultRepository.findByUserAndNameContainingIgnoreCaseAndArchivedFalse(user, search.trim(), pageable);
        } else {
            vaultPage = vaultRepository.findByUserAndArchivedFalse(user, pageable);
        }


        List<VaultResponse> content = vaultPage.getContent().stream()
                .map(vaultMapper::toResponse)
                .collect(Collectors.toList());


        Sort.Direction dir = direction;
        if ("totalAmount".equals(sortBy)) {
            content.sort((a, b) -> {
                BigDecimal aTotal = a.getTotalAmount() != null ? a.getTotalAmount() : BigDecimal.ZERO;
                BigDecimal bTotal = b.getTotalAmount() != null ? b.getTotalAmount() : BigDecimal.ZERO;
                return dir == Sort.Direction.ASC
                        ? aTotal.compareTo(bTotal)
                        : bTotal.compareTo(aTotal);
            });
        }

        return PageResponse.<VaultResponse>builder()
                .content(content)
                .pageNumber(vaultPage.getNumber() + 1)
                .pageSize(vaultPage.getSize())
                .totalElements(vaultPage.getTotalElements())
                .totalPages(vaultPage.getTotalPages())
                .first(vaultPage.isFirst())
                .last(vaultPage.isLast())
                .build();
    }

    @Transactional(readOnly = true)
    public VaultSummaryResponse getVaultSummary(User user, Long vaultId) {
        Vault vault = vaultRepository.findByIdAndUserAndArchivedFalse(vaultId, user)
                .orElseThrow(() -> new ApplicationException(
                        VAULT_NOT_FOUND.getCode(),
                        VAULT_NOT_FOUND.getMessage()
                ));

        List<Bullion> bullions = vault.getBullions();

        BigDecimal totalAmount = bullions.stream()
                .map(Bullion::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<VaultSummaryResponse.BullionByBullionNameResponse> bullionResponses = bullions.stream()
                .map(b -> VaultSummaryResponse.BullionByBullionNameResponse.builder()
                        .id(b.getId())
                        .bullionNameId(b.getBullionName().getId())
                        .bullionNameTitle(b.getBullionName().getTitle())
                        .amount(b.getAmount())
                        .description(b.getDescription())
                        .bullionType(b.getBullionType())
                        .build())
                .sorted((a, b) -> b.getAmount().compareTo(a.getAmount()))
                .toList();


        return VaultSummaryResponse.builder()
                .vaultId(vaultId)
                .vaultName(vault.getName())
                .totalAmount(totalAmount)
                .interestRate(vault.getInterestRate())
                .bullionNamesCount(bullions.size())
                .bullions(bullionResponses)
                .build();
    }


    @Transactional
    public Vault getLiquidityReserve(User user) {
        Optional<Vault> existingVault = vaultRepository.findVaultByUserAndVaultTypeAndArchivedFalse(user, VaultType.LIQUIDITY_BUFFER);
        if (existingVault.isPresent()) {
            log.info("[VaultService.getLiquidityReserve] liquidity reserve existing vault id = {}", existingVault.get().getId());
            return existingVault.get();
        }
        log.info("[VaultService.getLiquidityReserve] liquidity reserve not found. It will be create");
        Vault liquidityReserve = Vault.builder()
                .name(NAME_LIQUIDITY_RESERVE)
                .description(DESCRIPTION_LIQUIDITY_RESERVE)
                .interestRate(BigDecimal.ZERO)
                .vaultType(VaultType.LIQUIDITY_BUFFER)
                .accountType(AccountType.SAVINGS)
                .user(user)
                .build();
        vaultRepository.save(liquidityReserve);
        return liquidityReserve;
    }
}