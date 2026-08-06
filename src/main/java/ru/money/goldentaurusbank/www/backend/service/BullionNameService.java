package ru.money.goldentaurusbank.www.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import ru.money.goldentaurusbank.www.backend.infrastructure.exception.ApplicationException;
import ru.money.goldentaurusbank.www.backend.model.domain.BullionName;
import ru.money.goldentaurusbank.www.backend.model.domain.User;
import ru.money.goldentaurusbank.www.backend.model.dto.request.BullionNameRequest;
import ru.money.goldentaurusbank.www.backend.model.dto.response.BullionNameImportResult;
import ru.money.goldentaurusbank.www.backend.model.dto.response.BullionNameResponse;
import ru.money.goldentaurusbank.www.backend.repository.BullionRepository;
import ru.money.goldentaurusbank.www.backend.repository.BullionNameRepository;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static ru.money.goldentaurusbank.www.backend.model.dto.enums.ResponseCodes.*;


@Service
@RequiredArgsConstructor
@Slf4j
public class BullionNameService {

    private final BullionNameRepository bullionNameRepository;
    private final ColorConstants colorConstants;
    private final BullionRepository bullionRepository;

    public List<String> getAvailableColors(User user) {
        List<String> usedColors = bullionNameRepository.findUsedColorsByUser(user);
        return colorConstants.getAllColors().stream()
                .filter(color -> !usedColors.contains(color))
                .collect(Collectors.toList());
    }

    @Transactional
    public BullionNameResponse createBullionName(User user, BullionNameRequest request) {
        String title = request.getTitle().trim();

        Optional<BullionName> existingBullionName = bullionNameRepository.findByUserAndTitleIgnoreCase(user, title);

        if (existingBullionName.isPresent()) {
            return BullionNameResponse.fromBullionName(existingBullionName.get());
        }


        BullionName bullionName = BullionName.builder()
                .title(title)
                .user(user)
                .color(request.getColor())
                .build();
        bullionNameRepository.save(bullionName);

        return BullionNameResponse.fromBullionName(bullionName);
    }

    @Transactional(readOnly = true)
    public List<BullionNameResponse> getAllBullionNames(User user) {
        return bullionNameRepository.findByUserOrderByTitleAsc(user).stream()
                .map(BullionNameResponse::fromBullionName)
                .collect(Collectors.toList());
    }

    @Transactional
    public BullionNameResponse updateBullionName(User user, Long bullionNameId, BullionNameRequest request) {
        BullionName bullionName = bullionNameRepository.findByIdAndUser(bullionNameId, user)
                .orElseThrow(() -> new ApplicationException(
                        BULLION_NAME_NOT_FOUND.getCode(),
                        BULLION_NAME_NOT_FOUND.getMessage()
                ));

        String newTitle = request.getTitle().trim();

        boolean titleExists = bullionNameRepository.existsByUserAndTitleIgnoreCase(user, newTitle);

        if (!titleExists || bullionName.getTitle().equalsIgnoreCase(newTitle)) {
            bullionName.setTitle(newTitle);

        }

        if (request.getColor() != null && !request.getColor().isEmpty()) {

            if (!colorConstants.containsColor(request.getColor())) {
                throw new ApplicationException(
                        INTERNAL_ERROR.getCode(),
                        INVALID_COLOR.getMessage()
                );
            }


            validateColorAvailable(user, request.getColor(), bullionNameId);

            // Если цвет изменился - обновляем
            if (!request.getColor().equals(bullionName.getColor())) {
                bullionName.setColor(request.getColor());
            }
        }

        BullionName updated = bullionNameRepository.save(bullionName);
        return BullionNameResponse.fromBullionName(updated);
    }

    @Transactional
    public void deleteBullionName(User user, Long bullionNameId) {
        if (!bullionNameRepository.existsByIdAndUser(bullionNameId, user)) {
            throw new ApplicationException(
                    BULLION_NAME_NOT_FOUND.getCode(),
                    BULLION_NAME_NOT_FOUND.getMessage()
            );
        }
        if (bullionRepository.existsByBullionNameIdAndUserId(bullionNameId, user.getId())) {
            throw new ApplicationException(
                    BULLION_NAME_LINKED.getCode(), BULLION_NAME_LINKED.getMessage());
        }

        bullionNameRepository.deleteByIdAndUser(bullionNameId, user);
    }


    public byte[] exportBullionNamesToExcel(User user) throws IOException {
        List<BullionName> bullionNames = bullionNameRepository.findByUserOrderByTitleAsc(user);

        try (SXSSFWorkbook workbook = new SXSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Наименования");

            // Стиль для заголовка
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

            // Заголовок
            Row headerRow = sheet.createRow(0);
            Cell headerCell = headerRow.createCell(0);
            headerCell.setCellValue("Наименование слитка");
            headerCell.setCellStyle(headerStyle);
            sheet.setColumnWidth(0, 8000);

            // Данные
            int rowNum = 1;
            for (BullionName bullionName : bullionNames) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(bullionName.getTitle());
            }

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    // Импорт наименований слитков из Excel (добавляет только новые, пропускает дубликаты)
    @Transactional
    public BullionNameImportResult importBullionNamesFromExcel(User user, MultipartFile file) throws IOException {
        BullionNameImportResult result = BullionNameImportResult.builder().build();

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

        // Существующие наименования пользователя
        Set<String> existingBullionNameTitles = bullionNameRepository.findByUserOrderByTitleAsc(user).stream()
                .map(BullionName::getTitle)
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

        List<String> bullionNamesToAdd = new ArrayList<>();

        try (InputStream inputStream = file.getInputStream();
             Workbook workbook = new XSSFWorkbook(inputStream)) {

            Sheet sheet = workbook.getSheetAt(0);

            // Пропускаем заголовок
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                Cell cell = row.getCell(0);
                if (cell == null) continue;

                String bullionNameTitle = getCellValueAsString(cell).trim();
                if (bullionNameTitle.isEmpty()) continue;

                // Валидация
                if (bullionNameTitle.length() < 2) {
                    result.getErrors().add("Строка " + (i + 1) + ": '" + bullionNameTitle + "' - название слишком короткое (мин. 2 символа)");
                    continue;
                }

                if (bullionNameTitle.length() > 100) {
                    result.getErrors().add("Строка " + (i + 1) + ": '" + bullionNameTitle + "' - название слишком длинное (макс. 100 символов)");
                    continue;
                }

                // Проверка на дубликат
                if (existingBullionNameTitles.contains(bullionNameTitle.toLowerCase())) {
                    result.getSkippedBullionNames().add(bullionNameTitle);
                    result.setSkipped(result.getSkipped() + 1);
                } else if (bullionNamesToAdd.stream().anyMatch(c -> c.equalsIgnoreCase(bullionNameTitle))) {
                    result.getErrors().add("Строка " + (i + 1) + ": '" + bullionNameTitle + "' - дубликат внутри файла");
                } else {
                    bullionNamesToAdd.add(bullionNameTitle);
                }
            }

            // Сохраняем новые наименования
            for (String bullionNameTitle : bullionNamesToAdd) {
                BullionName bullionName = BullionName.builder()
                        .title(bullionNameTitle)
                        .user(user)
                        .build();
                bullionNameRepository.save(bullionName);
                result.getAddedBullionNames().add(bullionNameTitle);
            }

            result.setTotalProcessed(bullionNamesToAdd.size() + result.getSkipped());
            result.setAdded(bullionNamesToAdd.size());

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

    private void validateColorAvailable(User user, String color, Long excludeBullionNameId) {
        if (color == null) return;

        List<String> usedColors = bullionNameRepository.findUsedColorsByUser(user);

        // При редактировании исключаем текущее наименование
        if (excludeBullionNameId != null) {
            BullionName currentBullionName = bullionNameRepository.findById(excludeBullionNameId)
                    .orElseThrow(() -> new ApplicationException(
                            BULLION_NAME_NOT_FOUND.getCode(),
                            BULLION_NAME_NOT_FOUND.getMessage()
                    ));
            usedColors.remove(currentBullionName.getColor());
        }

        if (usedColors.contains(color)) {
            throw new ApplicationException(
                    COLOR_ALREADY_USE.getCode(),
                    COLOR_ALREADY_USE.getMessage()
            );
        }
    }
}
