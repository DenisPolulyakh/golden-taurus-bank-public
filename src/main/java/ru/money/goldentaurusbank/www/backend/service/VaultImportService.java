// VaultImportService.java
package ru.money.goldentaurusbank.www.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import ru.money.goldentaurusbank.www.backend.infrastructure.exception.ApplicationException;
import ru.money.goldentaurusbank.www.backend.model.domain.User;
import ru.money.goldentaurusbank.www.backend.model.domain.Vault;
import ru.money.goldentaurusbank.www.backend.model.dto.enums.AccountType;
import ru.money.goldentaurusbank.www.backend.model.dto.enums.VaultType;
import ru.money.goldentaurusbank.www.backend.model.dto.request.*;
import ru.money.goldentaurusbank.www.backend.model.dto.response.VaultImportResult;
import ru.money.goldentaurusbank.www.backend.model.dto.response.VaultResponse;
import ru.money.goldentaurusbank.www.backend.repository.BullionRepository;
import ru.money.goldentaurusbank.www.backend.repository.CategoryRepository;
import ru.money.goldentaurusbank.www.backend.repository.VaultRepository;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class VaultImportService {

    private final BankService bankService;
    private final CategoryService categoryService;
    private final BullionService bullionService;
    private final VaultService vaultService;
    private final CategoryRepository categoryRepository;
    private final BullionRepository bullionRepository;

    @Transactional
    public VaultImportResult importVaultsFromExcel(User user, MultipartFile file) throws IOException {
        VaultImportResult result = new VaultImportResult();

        if (file.isEmpty()) {
            result.getErrors().add("Файл пуст");
            return result;
        }

        String filename = file.getOriginalFilename();
        if (filename == null || (!filename.endsWith(".xlsx") && !filename.endsWith(".xls"))) {
            result.getErrors().add("Неверный формат файла. Поддерживаются .xlsx и .xls");
            return result;
        }

        try (InputStream inputStream = file.getInputStream();
             Workbook workbook = new XSSFWorkbook(inputStream)) {

            Sheet sheet = workbook.getSheetAt(0);

            // 1. Читаем данные из Excel
            String bankName = getCellValue(sheet.getRow(0).getCell(1)).trim();
            String accountTypeStr = getCellValue(sheet.getRow(1).getCell(1)).trim();
            String rateStr = getCellValue(sheet.getRow(3).getCell(1)).trim();
            String vaultName = getCellValue(sheet.getRow(5).getCell(1)).trim();

            // 2. Создаем банк через BankService
            BankRequest bankRequest = new BankRequest();
            bankRequest.setName(bankName);
            var bankResponse = bankService.createBank(user, bankRequest);

            // 3. Создаем хранилище через VaultService
            VaultRequest vaultRequest = new VaultRequest();
            vaultRequest.setName(vaultName);
            vaultRequest.setBankId(bankResponse.getId());
            vaultRequest.setInterestRate(parseInterestRate(rateStr));
            vaultRequest.setAccountType(parseAccountType(accountTypeStr));
            vaultRequest.setVaultType(VaultType.REGULAR);
            vaultRequest.setDescription("Импортировано из Excel");

            Vault   vault = vaultService.createVault(user, vaultRequest);


            result.setVaultsAdded(1);
            result.getAddedVaults().add(vault.getName());

            // 4. Читаем категории и создаем слитки
            int startRow = 7;
            for (int i = startRow; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                String categoryName = getCellValue(row.getCell(0)).trim();
                if (categoryName.isEmpty()) continue;

                if (categoryName.equalsIgnoreCase("Итого") ||
                        categoryName.equalsIgnoreCase("Итого было")) {
                    continue;
                }

                String totalStr = getCellValue(row.getCell(7)).trim();
                BigDecimal amount = parseBigDecimal(totalStr);

                if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }

                // 5. Создаем категорию через CategoryService
                Cell categoryCell = row.getCell(8);
                String cellColor = getCellColor(categoryCell);

                CategoryRequest categoryRequest = new CategoryRequest();
                categoryRequest.setName(categoryName);
                if (cellColor != null && !cellColor.isEmpty()) {
                    categoryRequest.setColor(cellColor);
                }
                categoryService.createCategory(user, categoryRequest);

                result.setCategoriesAdded(result.getCategoriesAdded() + 1);
                result.getAddedCategories().add(categoryName);

                // 6. Находим ID категории
                var categoryOpt = categoryRepository.findByUserAndNameIgnoreCase(user, categoryName);
                if (categoryOpt.isEmpty()) {
                    result.getErrors().add("Категория не найдена: " + categoryName);
                    continue;
                }

                // 7. Создаем слиток через BullionService
                try {
                    BullionRequest bullionRequest = new BullionRequest();
                    bullionRequest.setCategoryId(categoryOpt.get().getId());
                    bullionRequest.setVaultId(vault.getId());
                    bullionRequest.setAmount(amount);
                    bullionRequest.setDescription("Импортировано из Excel");
                    bullionService.createBullion(user, bullionRequest);

                    result.setBullionsAdded(result.getBullionsAdded() + 1);
                } catch (Exception e) {
                    result.getErrors().add("Ошибка создания слитка для категории " + categoryName + ": " + e.getMessage());
                }
            }

        } catch (Exception e) {
            log.error("Ошибка при импорте файла хранилищ", e);
            result.getErrors().add("Ошибка чтения файла: " + e.getMessage());
        }

        return result;
    }

    /**
     * Получить цвет заливки ячейки в HEX формате (#RRGGBB)
     */
    private String getCellColor(Cell cell) {
        if (cell == null) return null;

        try {
            Workbook workbook = cell.getSheet().getWorkbook();
            CellStyle style = cell.getCellStyle();
            if (style == null) return null;

            // Для .xlsx файлов
            if (workbook instanceof XSSFWorkbook) {
                XSSFColor color = (XSSFColor) style.getFillForegroundColorColor();
                if (color != null) {
                    byte[] rgb = color.getRGB();
                    if (rgb != null && rgb.length >= 3) {
                        // Пропускаем белый цвет (фон по умолчанию)
                        if (rgb[0] == (byte)255 && rgb[1] == (byte)255 && rgb[2] == (byte)255) {
                            return null;
                        }
                        return String.format("#%02X%02X%02X", rgb[0] & 0xFF, rgb[1] & 0xFF, rgb[2] & 0xFF);
                    }
                }
            }

            // Для .xls файлов
            short colorIndex = style.getFillForegroundColor();
            if (colorIndex != 0) {
                return convertIndexedColorToHex(colorIndex);
            }

            return null;
        } catch (Exception e) {
            log.warn("Не удалось прочитать цвет ячейки: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Конвертировать индекс стандартного цвета Excel в HEX
     */
    private String convertIndexedColorToHex(short colorIndex) {
        Map<Short, String> colorMap = new HashMap<>();
        colorMap.put((short) 3, "#FF0000");   // Red
        colorMap.put((short) 4, "#00FF00");   // Green
        colorMap.put((short) 5, "#0000FF");   // Blue
        colorMap.put((short) 6, "#FFFF00");   // Yellow
        colorMap.put((short) 7, "#FF00FF");   // Magenta
        colorMap.put((short) 8, "#00FFFF");   // Cyan
        colorMap.put((short) 9, "#800000");   // Dark Red
        colorMap.put((short) 10, "#008000");  // Dark Green
        colorMap.put((short) 11, "#000080");  // Dark Blue
        colorMap.put((short) 12, "#808000");  // Olive
        colorMap.put((short) 13, "#800080");  // Purple
        colorMap.put((short) 14, "#008080");  // Teal
        colorMap.put((short) 45, "#FF9900");  // Orange
        colorMap.put((short) 46, "#FF6600");  // Dark Orange


        return colorMap.get(colorIndex);
    }

    private BigDecimal parseInterestRate(String value) {
        if (value == null || value.isEmpty()) return BigDecimal.ZERO;

        String clean = value.replace(",", ".").replaceAll("\\s", "");
        clean = clean.replaceAll("[₽$€%]", "").trim();

        if (clean.equals("-") || clean.isEmpty()) {
            return BigDecimal.ZERO;
        }

        try {
            BigDecimal rate = new BigDecimal(clean);
            if (rate.compareTo(BigDecimal.ONE) < 0 && rate.compareTo(BigDecimal.ZERO) > 0) {
                rate = rate.multiply(new BigDecimal("100"));
            }
            return rate.setScale(2, java.math.RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private AccountType parseAccountType(String value) {
        if (value == null) return AccountType.SAVINGS;
        if (value.equalsIgnoreCase("Накопительный") || value.equalsIgnoreCase("SAVINGS")) {
            return AccountType.SAVINGS;
        }
        if (value.equalsIgnoreCase("Срочный") || value.equalsIgnoreCase("TERM")) {
            return AccountType.TERM;
        }
        return AccountType.SAVINGS;
    }

    private BigDecimal parseBigDecimal(String value) {
        if (value == null || value.isEmpty()) return BigDecimal.ZERO;

        String clean = value.replace(",", ".").replaceAll("\\s", "");
        clean = clean.replaceAll("[₽$€%]", "").trim();

        if (clean.equals("-") || clean.isEmpty()) {
            return BigDecimal.ZERO;
        }

        try {
            return new BigDecimal(clean);
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private String getCellValue(Cell cell) {
        if (cell == null) return "";
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
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