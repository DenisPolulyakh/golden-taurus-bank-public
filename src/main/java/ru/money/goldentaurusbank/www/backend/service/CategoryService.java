package ru.money.goldentaurusbank.www.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import ru.money.goldentaurusbank.www.backend.infrastructure.exception.ApplicationException;
import ru.money.goldentaurusbank.www.backend.model.domain.Category;
import ru.money.goldentaurusbank.www.backend.model.domain.User;
import ru.money.goldentaurusbank.www.backend.model.dto.request.CategoryRequest;
import ru.money.goldentaurusbank.www.backend.model.dto.response.CategoryImportResult;
import ru.money.goldentaurusbank.www.backend.model.dto.response.CategoryResponse;
import ru.money.goldentaurusbank.www.backend.repository.CategoryRepository;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import ru.money.goldentaurusbank.www.backend.repository.UserRepository;

import static ru.money.goldentaurusbank.www.backend.model.dto.enums.ResponseCodes.*;


@Service
@RequiredArgsConstructor
@Slf4j
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final ColorConstants colorConstants;


    public List<String> getAvailableColors(User user) {
        List<String> usedColors = categoryRepository.findUsedColorsByUser(user);
        return colorConstants.getAllColors().stream()
                .filter(color -> !usedColors.contains(color))
                .collect(Collectors.toList());
    }

    @Transactional
    public CategoryResponse createCategory(User user, CategoryRequest request) {
        String name = request.getName().trim();

        Optional<Category> existingCategory = categoryRepository.findByUserAndNameIgnoreCase(user, name);

        if (existingCategory.isPresent()) {
            return CategoryResponse.fromCategory(existingCategory.get());
        }


        Category category = Category.builder()
                .name(name)
                .user(user)
                .color(request.getColor())
                .build();
        categoryRepository.save(category);

        return CategoryResponse.fromCategory(category);
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> getAllCategories(User user) {
        return categoryRepository.findByUserOrderByNameAsc(user).stream()
                .map(CategoryResponse::fromCategory)
                .collect(Collectors.toList());
    }

    @Transactional
    public CategoryResponse updateCategory(User user, Long categoryId, CategoryRequest request) {
        Category category = categoryRepository.findByIdAndUser(categoryId, user)
                .orElseThrow(() -> new ApplicationException(
                        CATEGORY_NOT_FOUND.getCode(),
                        CATEGORY_NOT_FOUND.getMessage()
                ));

        String newName = request.getName().trim();

        boolean nameExists = categoryRepository.existsByUserAndNameIgnoreCase(user, newName);

        if (!nameExists || category.getName().equalsIgnoreCase(newName)) {
            category.setName(newName);

        }

        if (request.getColor() != null && !request.getColor().isEmpty()) {

            if (!colorConstants.containsColor(request.getColor())) {
                throw new ApplicationException(
                        INTERNAL_ERROR.getCode(),
                        INVALID_COLOR.getMessage()
                );
            }


            validateColorAvailable(user, request.getColor(), categoryId);

            // Если цвет изменился - обновляем
            if (!request.getColor().equals(category.getColor())) {
                category.setColor(request.getColor());
            }
        }

        Category updated = categoryRepository.save(category);
        return CategoryResponse.fromCategory(updated);
    }

    @Transactional
    public void deleteCategory(User user, Long categoryId) {
        if (!categoryRepository.existsByIdAndUser(categoryId, user)) {
            throw new ApplicationException(
                    CATEGORY_NOT_FOUND.getCode(),
                    CATEGORY_NOT_FOUND.getMessage()
            );
        }
        categoryRepository.deleteByIdAndUser(categoryId, user);
    }


    public byte[] exportCategoriesToExcel(User user) throws IOException {
        List<Category> categories = categoryRepository.findByUserOrderByNameAsc(user);

        try (SXSSFWorkbook workbook = new SXSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Категории");

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
            headerCell.setCellValue("Название категории");
            headerCell.setCellStyle(headerStyle);
            sheet.setColumnWidth(0, 8000);

            // Данные
            int rowNum = 1;
            for (Category category : categories) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(category.getName());
            }

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    // Импорт категорий из Excel (добавляет только новые, пропускает дубликаты)
    @Transactional
    public CategoryImportResult importCategoriesFromExcel(User user, MultipartFile file) throws IOException {
        CategoryImportResult result = CategoryImportResult.builder().build();

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

        // Существующие категории пользователя
        Set<String> existingCategoryNames = categoryRepository.findByUserOrderByNameAsc(user).stream()
                .map(Category::getName)
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

        List<String> categoriesToAdd = new ArrayList<>();

        try (InputStream inputStream = file.getInputStream();
             Workbook workbook = new XSSFWorkbook(inputStream)) {

            Sheet sheet = workbook.getSheetAt(0);

            // Пропускаем заголовок
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                Cell cell = row.getCell(0);
                if (cell == null) continue;

                String categoryName = getCellValueAsString(cell).trim();
                if (categoryName.isEmpty()) continue;

                // Валидация
                if (categoryName.length() < 2) {
                    result.getErrors().add("Строка " + (i + 1) + ": '" + categoryName + "' - название слишком короткое (мин. 2 символа)");
                    continue;
                }

                if (categoryName.length() > 100) {
                    result.getErrors().add("Строка " + (i + 1) + ": '" + categoryName + "' - название слишком длинное (макс. 100 символов)");
                    continue;
                }

                // Проверка на дубликат
                if (existingCategoryNames.contains(categoryName.toLowerCase())) {
                    result.getSkippedCategories().add(categoryName);
                    result.setSkipped(result.getSkipped() + 1);
                } else if (categoriesToAdd.stream().anyMatch(c -> c.equalsIgnoreCase(categoryName))) {
                    result.getErrors().add("Строка " + (i + 1) + ": '" + categoryName + "' - дубликат внутри файла");
                } else {
                    categoriesToAdd.add(categoryName);
                }
            }

            // Сохраняем новые категории
            for (String categoryName : categoriesToAdd) {
                Category category = Category.builder()
                        .name(categoryName)
                        .user(user)
                        .build();
                categoryRepository.save(category);
                result.getAddedCategories().add(categoryName);
            }

            result.setTotalProcessed(categoriesToAdd.size() + result.getSkipped());
            result.setAdded(categoriesToAdd.size());

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

    private void validateColorAvailable(User user, String color, Long excludeCategoryId) {
        if (color == null) return;

        List<String> usedColors = categoryRepository.findUsedColorsByUser(user);

        // При редактировании исключаем текущую категорию
        if (excludeCategoryId != null) {
            Category currentCategory = categoryRepository.findById(excludeCategoryId)
                    .orElseThrow(() -> new ApplicationException(
                            CATEGORY_NOT_FOUND.getCode(),
                            CATEGORY_NOT_FOUND.getMessage()
                    ));
            usedColors.remove(currentCategory.getColor());
        }

        if (usedColors.contains(color)) {
            throw new ApplicationException(
                    COLOR_ALREADY_USE.getCode(),
                    COLOR_ALREADY_USE.getMessage()
            );
        }
    }
}
