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
import ru.money.goldentaurusbank.www.backend.model.dto.request.BullionNameRequest;
import ru.money.goldentaurusbank.www.backend.model.dto.response.BullionNameImportResult;
import ru.money.goldentaurusbank.www.backend.model.dto.response.BullionNameResponse;
import ru.money.goldentaurusbank.www.backend.model.dto.response.SuccessResponse;
import ru.money.goldentaurusbank.www.backend.repository.UserRepository;
import ru.money.goldentaurusbank.www.backend.service.BullionNameService;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;


import static ru.money.goldentaurusbank.www.backend.model.dto.enums.ResponseCodes.USER_NOT_FOUND;

@RestController
@RequestMapping("/api/bullion-names")
@RequiredArgsConstructor
public class BullionNameController {

    private final BullionNameService bullionNameService;
    private final UserRepository userRepository;



    @GetMapping("/colors/available")
    public ResponseEntity<SuccessResponse<List<String>>> getAvailableColors(
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = getUserFromUserDetails(userDetails);
        List<String> availableColors = bullionNameService.getAvailableColors(user);

        return ResponseEntity.ok(new SuccessResponse<>(availableColors));
    }

    // Добавление наименования слитка
    @PostMapping
    public ResponseEntity<SuccessResponse<BullionNameResponse>> createBullionName(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody BullionNameRequest request) {

        User user = getUserFromUserDetails(userDetails);
        BullionNameResponse response = bullionNameService.createBullionName(user, request);

        return ResponseEntity.ok(new SuccessResponse<>(
                0,
                "Наименование слитка успешно добавлено",
                response
        ));
    }

    // Получение всех наименований слитков пользователя
    @GetMapping
    public ResponseEntity<SuccessResponse<List<BullionNameResponse>>> getAllBullionNames(
            @AuthenticationPrincipal UserDetails userDetails) {
        
        User user = getUserFromUserDetails(userDetails);
        List<BullionNameResponse> bullionNames = bullionNameService.getAllBullionNames(user);
        
        return ResponseEntity.ok(new SuccessResponse<>(bullionNames));
    }

    // Обновление наименования слитка
    @PutMapping("/{bullionNameId}")
    public ResponseEntity<SuccessResponse<BullionNameResponse>> updateBullionName(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long bullionNameId,
            @Valid @RequestBody BullionNameRequest request) {

        User user = getUserFromUserDetails(userDetails);
        BullionNameResponse response = bullionNameService.updateBullionName(user, bullionNameId, request);

        return ResponseEntity.ok(new SuccessResponse<>(
                0,
                "Наименование слитка успешно обновлено",
                response
        ));
    }

    // Удаление наименования слитка
    @DeleteMapping("/{bullionNameId}")
    public ResponseEntity<SuccessResponse<Void>> deleteBullionName(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long bullionNameId) {

        User user = getUserFromUserDetails(userDetails);
        bullionNameService.deleteBullionName(user, bullionNameId);

        return ResponseEntity.ok(new SuccessResponse<>("Наименование слитка успешно удалено"));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportBullionNames(
            @AuthenticationPrincipal UserDetails userDetails) throws IOException {

        User user = getUserFromUserDetails(userDetails);
        byte[] excelData = bullionNameService.exportBullionNamesToExcel(user);

        String filename = URLEncoder.encode("bullion-names.xlsx", StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(excelData);
    }

    // Импорт наименований слитков из Excel
    @PostMapping("/import")
    public ResponseEntity<SuccessResponse<BullionNameImportResult>> importBullionNames(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam("file") MultipartFile file) throws IOException {

        User user = getUserFromUserDetails(userDetails);
        BullionNameImportResult result = bullionNameService.importBullionNamesFromExcel(user, file);

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