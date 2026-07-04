package ru.money.goldentaurusbank.www.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import ru.money.goldentaurusbank.www.backend.infrastructure.exception.ApplicationException;
import ru.money.goldentaurusbank.www.backend.model.domain.BankDictionary;
import ru.money.goldentaurusbank.www.backend.model.domain.User;
import ru.money.goldentaurusbank.www.backend.model.domain.Vault;
import ru.money.goldentaurusbank.www.backend.model.dto.request.BankRequest;
import ru.money.goldentaurusbank.www.backend.model.dto.response.*;
import ru.money.goldentaurusbank.www.backend.model.mapper.VaultMapper;
import ru.money.goldentaurusbank.www.backend.repository.BankRepository;
import ru.money.goldentaurusbank.www.backend.repository.UserRepository;
import ru.money.goldentaurusbank.www.backend.repository.VaultRepository;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

import static ru.money.goldentaurusbank.www.backend.model.dto.enums.ResponseCodes.BANK_NOT_FOUND;

@Slf4j
@Service
@RequiredArgsConstructor
public class BankService {

    private final BankRepository bankRepository;
    private final VaultRepository vaultRepository;
    private final VaultMapper vaultMapper;


    @Transactional(readOnly = true)
    public BankDetailResponse getBankDetail(User user, Long bankId, String search, String sortBy, String sortOrder, int page, int size) {
        // 1. Проверяем существование банка
        BankDictionary bank = bankRepository.findByIdAndUser(bankId, user)
                .orElseThrow(() -> new ApplicationException(
                        BANK_NOT_FOUND.getCode(),
                        BANK_NOT_FOUND.getMessage()
                ));

        // 2. Получаем все хранилища этого банка
        List<Vault> allBankVaults = vaultRepository.findByUserAndBank(user, bank);

        // 3. Вычисляем общую сумму
        BigDecimal totalAmount = allBankVaults.stream()
                .map(v -> v.getBullions().stream()
                        .map(b -> b.getAmount() != null ? b.getAmount() : BigDecimal.ZERO)
                        .reduce(BigDecimal.ZERO, BigDecimal::add))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 4. Фильтрация по поиску
        List<Vault> filteredVaults = allBankVaults;
        if (search != null && !search.trim().isEmpty()) {
            String searchLower = search.trim().toLowerCase();
            filteredVaults = allBankVaults.stream()
                    .filter(v -> v.getName().toLowerCase().contains(searchLower))
                    .collect(Collectors.toList());
        }

        // 5. Сортировка
        Comparator<Vault> comparator = Comparator.comparing(Vault::getName);
        if (sortBy != null) {
            comparator = switch (sortBy) {
                case "name" -> Comparator.comparing(Vault::getName);
                case "interestRate" -> Comparator.comparing(Vault::getInterestRate);
                case "totalAmount" -> Comparator.comparing(v -> v.getBullions().stream()
                        .map(b -> b.getAmount() != null ? b.getAmount() : BigDecimal.ZERO)
                        .reduce(BigDecimal.ZERO, BigDecimal::add));
                default -> Comparator.comparing(Vault::getName);
            };
        }

        if (sortOrder != null && sortOrder.equalsIgnoreCase("desc")) {
            comparator = comparator.reversed();
        }

        filteredVaults.sort(comparator);

        // 6. Пагинация
        int pageNumber = Math.max(page - 1, 0);
        int pageSize = size > 0 ? Math.min(size, 100) : 10;

        int totalElements = filteredVaults.size();
        int start = Math.min(pageNumber * pageSize, totalElements);
        int end = Math.min(start + pageSize, totalElements);
        List<Vault> pagedVaults = filteredVaults.subList(start, end);

        // 7. Маппинг в VaultResponse
        List<VaultResponse> vaultResponses = pagedVaults.stream()
                .map(vaultMapper::toResponse)
                .collect(Collectors.toList());

        PageResponse<VaultResponse> pageResponse = PageResponse.<VaultResponse>builder()
                .content(vaultResponses)
                .pageNumber(pageNumber + 1)
                .pageSize(pageSize)
                .totalElements(totalElements)
                .totalPages((int) Math.ceil((double) totalElements / pageSize))
                .first(start == 0)
                .last(end >= totalElements)
                .build();

        // 8. Формируем ответ
        return BankDetailResponse.builder()
                .bankId(bank.getId())
                .bankName(bank.getName())
                .totalAmount(totalAmount)
                .vaultsCount(allBankVaults.size())
                .vaults(pageResponse)
                .build();
    }

    @Transactional
    public BankResponse createBank(User user, BankRequest request) {
        String name = request.getName().trim();

        Optional<BankDictionary> existingBank = bankRepository.findByUserAndNameIgnoreCase(user, name);

        if (existingBank.isPresent()) {
            return BankResponse.fromBank(existingBank.get());
        }

        BankDictionary bank = BankDictionary.builder()
                .name(name)
                .user(user)
                .build();
        bankRepository.save(bank);

        return BankResponse.fromBank(bank);
    }

    @Transactional(readOnly = true)
    public List<BankResponse> getAllBanks(User user) {
        return bankRepository.findByUserOrderByNameAsc(user).stream()
                .map(BankResponse::fromBank)
                .collect(Collectors.toList());
    }

    @Transactional
    public BankResponse updateBank(User user, Long bankId, BankRequest request) {
        BankDictionary bank = bankRepository.findByIdAndUser(bankId, user)
                .orElseThrow(() -> new ApplicationException(
                        BANK_NOT_FOUND.getCode(),
                        BANK_NOT_FOUND.getMessage()
                ));

        String newName = request.getName().trim();

        boolean nameExists = bankRepository.existsByUserAndNameIgnoreCase(user, newName);

        if (!nameExists || bank.getName().equalsIgnoreCase(newName)) {
            bank.setName(newName);
            bankRepository.save(bank);
        }

        return BankResponse.fromBank(bank);
    }

    @Transactional
    public void deleteBank(User user, Long bankId) {
        if (!bankRepository.existsByIdAndUser(bankId, user)) {
            throw new ApplicationException(
                    BANK_NOT_FOUND.getCode(),
                    BANK_NOT_FOUND.getMessage()
            );
        }
        bankRepository.deleteByIdAndUser(bankId, user);
    }

    // Экспорт банков в Excel
    public byte[] exportBanksToExcel(User user) throws IOException {
        List<BankDictionary> banks = bankRepository.findByUserOrderByNameAsc(user);
        
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Банки");
            
            // Создаем стиль для заголовка
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);
            
            // Создаем заголовок
            Row headerRow = sheet.createRow(0);
            Cell headerCell = headerRow.createCell(0);
            headerCell.setCellValue("Название банка");
            headerCell.setCellStyle(headerStyle);
            sheet.setColumnWidth(0, 8000);
            
            // Заполняем данные
            int rowNum = 1;
            for (BankDictionary bank : banks) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(bank.getName());
            }
            
            // Записываем в байтовый массив
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }
    
     @Transactional
    public BankImportResult importBanksFromExcel(User user, MultipartFile file) throws IOException {
        BankImportResult result = BankImportResult.builder().build();
        
        // Валидация файла
        if (file.isEmpty()) {
            result.getErrors().add("Файл пуст");
            return result;
        }
        
        String filename = file.getOriginalFilename();
        if (filename == null || (!filename.endsWith(".xlsx") && !filename.endsWith(".xls"))) {
            result.getErrors().add("Неверный формат файла. Поддерживаются .xlsx и .xls");
            return result;
        }
        
        // Получаем существующие банки пользователя для проверки дубликатов
        Set<String> existingBankNames = bankRepository.findByUserOrderByNameAsc(user).stream()
                .map(BankDictionary::getName)
                .map(String::toLowerCase)
                .collect(Collectors.toSet());
        
        List<String> banksToAdd = new ArrayList<>();
        
        try (InputStream inputStream = file.getInputStream();
             Workbook workbook = new XSSFWorkbook(inputStream)) {
            
            Sheet sheet = workbook.getSheetAt(0);
            
            // Пропускаем заголовок (первая строка)
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                
                Cell cell = row.getCell(0);
                if (cell == null) continue;
                
                String bankName = getCellValueAsString(cell).trim();
                if (bankName.isEmpty()) continue;
                
                // Валидация длины названия
                if (bankName.length() < 2) {
                    result.getErrors().add("Строка " + (i + 1) + ": '" + bankName + "' - название слишком короткое (мин. 2 символа)");
                    continue;
                }
                
                if (bankName.length() > 100) {
                    result.getErrors().add("Строка " + (i + 1) + ": '" + bankName + "' - название слишком длинное (макс. 100 символов)");
                    continue;
                }
                
                // Проверка на дубликат
                if (existingBankNames.contains(bankName.toLowerCase())) {
                    result.getSkippedBanks().add(bankName);
                    result.setSkipped(result.getSkipped() + 1);
                } else if (banksToAdd.stream().anyMatch(b -> b.equalsIgnoreCase(bankName))) {
                    // Дубликат внутри файла
                    result.getErrors().add("Строка " + (i + 1) + ": '" + bankName + "' - дубликат внутри файла");
                } else {
                    banksToAdd.add(bankName);
                }
            }
            
            // Сохраняем новые банки
            for (String bankName : banksToAdd) {
                BankDictionary bank = BankDictionary.builder()
                        .name(bankName)
                        .user(user)
                        .build();
                bankRepository.save(bank);
                result.getAddedBanks().add(bankName);
            }
            
            result.setTotalProcessed(banksToAdd.size() + result.getSkipped());
            result.setAdded(banksToAdd.size());
            
        } catch (Exception e) {
            log.error("Ошибка при импорте файла", e);
            result.getErrors().add("Ошибка чтения файла: " + e.getMessage());
        }
        
        return result;
    }
    
    private String getCellValueAsString(Cell cell) {
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                // Если число, преобразуем в строку без .0
                double numericValue = cell.getNumericCellValue();
                if (numericValue == (long) numericValue) {
                    return String.valueOf((long) numericValue);
                }
                return String.valueOf(numericValue);
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try {
                    return cell.getStringCellValue();
                } catch (IllegalStateException e) {
                    return String.valueOf(cell.getNumericCellValue());
                }
            default:
                return "";
        }
    }
}