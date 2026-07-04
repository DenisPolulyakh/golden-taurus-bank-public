package ru.money.goldentaurusbank.www.backend.infrastructure.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import ru.money.goldentaurusbank.www.backend.infrastructure.exception.ApplicationException;
import ru.money.goldentaurusbank.www.backend.model.domain.User;
import ru.money.goldentaurusbank.www.backend.model.dto.request.CategoryRequest;
import ru.money.goldentaurusbank.www.backend.model.dto.response.CategoryImportResult;
import ru.money.goldentaurusbank.www.backend.model.dto.response.CategoryResponse;
import ru.money.goldentaurusbank.www.backend.model.dto.response.SuccessResponse;
import ru.money.goldentaurusbank.www.backend.repository.UserRepository;
import ru.money.goldentaurusbank.www.backend.service.CategoryService;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;


import static ru.money.goldentaurusbank.www.backend.model.dto.enums.ResponseCodes.USER_NOT_FOUND;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;
    private final UserRepository userRepository;



    @GetMapping("/colors/available")
    public ResponseEntity<SuccessResponse<List<String>>> getAvailableColors(
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = getUserFromUserDetails(userDetails);
        List<String> availableColors = categoryService.getAvailableColors(user);

        return ResponseEntity.ok(new SuccessResponse<>(availableColors));
    }

    // Добавление категории
    @PostMapping
    public ResponseEntity<SuccessResponse<CategoryResponse>> createCategory(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody CategoryRequest request) {
        
        User user = getUserFromUserDetails(userDetails);
        CategoryResponse response = categoryService.createCategory(user, request);
        
        return ResponseEntity.ok(new SuccessResponse<>(
                0,
                "Категория успешно добавлена",
                response
        ));
    }

    // Получение всех категорий пользователя
    @GetMapping
    public ResponseEntity<SuccessResponse<List<CategoryResponse>>> getAllCategories(
            @AuthenticationPrincipal UserDetails userDetails) {
        
        User user = getUserFromUserDetails(userDetails);
        List<CategoryResponse> categories = categoryService.getAllCategories(user);
        
        return ResponseEntity.ok(new SuccessResponse<>(categories));
    }

    // Обновление категории
    @PutMapping("/{categoryId}")
    public ResponseEntity<SuccessResponse<CategoryResponse>> updateCategory(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long categoryId,
            @Valid @RequestBody CategoryRequest request) {
        
        User user = getUserFromUserDetails(userDetails);
        CategoryResponse response = categoryService.updateCategory(user, categoryId, request);
        
        return ResponseEntity.ok(new SuccessResponse<>(
                0,
                "Категория успешно обновлена",
                response
        ));
    }

    // Удаление категории
    @DeleteMapping("/{categoryId}")
    public ResponseEntity<SuccessResponse<Void>> deleteCategory(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long categoryId) {
        
        User user = getUserFromUserDetails(userDetails);
        categoryService.deleteCategory(user, categoryId);
        
        return ResponseEntity.ok(new SuccessResponse<>("Категория успешно удалена"));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportCategories(
            @AuthenticationPrincipal UserDetails userDetails) throws IOException {

        User user = getUserFromUserDetails(userDetails);
        byte[] excelData = categoryService.exportCategoriesToExcel(user);

        String filename = URLEncoder.encode("categories.xlsx", StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(excelData);
    }

    // Импорт категорий из Excel
    @PostMapping("/import")
    public ResponseEntity<SuccessResponse<CategoryImportResult>> importCategories(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam("file") MultipartFile file) throws IOException {

        User user = getUserFromUserDetails(userDetails);
        CategoryImportResult result = categoryService.importCategoriesFromExcel(user, file);

        String message = String.format("Импорт завершен. Добавлено: %d, Пропущено (дубликаты): %d",
                result.getAdded(), result.getSkipped());

        return ResponseEntity.ok(new SuccessResponse<>(0, message, result));
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