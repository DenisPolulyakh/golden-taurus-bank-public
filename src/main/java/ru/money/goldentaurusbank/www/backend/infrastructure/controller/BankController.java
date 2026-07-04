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
import ru.money.goldentaurusbank.www.backend.model.dto.request.BankRequest;
import ru.money.goldentaurusbank.www.backend.model.dto.response.BankDetailResponse;
import ru.money.goldentaurusbank.www.backend.model.dto.response.BankImportResult;
import ru.money.goldentaurusbank.www.backend.model.dto.response.BankResponse;
import ru.money.goldentaurusbank.www.backend.model.dto.response.SuccessResponse;
import ru.money.goldentaurusbank.www.backend.repository.UserRepository;
import ru.money.goldentaurusbank.www.backend.service.BankService;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static ru.money.goldentaurusbank.www.backend.model.dto.enums.ResponseCodes.USER_NOT_FOUND;

@RestController
@RequestMapping("/api/banks")
@RequiredArgsConstructor
public class BankController {

    private final BankService bankService;
    private final UserRepository userRepository;


    @GetMapping("/{bankId}")
    public ResponseEntity<SuccessResponse<BankDetailResponse>> getBankById(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long bankId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortOrder,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {

        User user = getUserFromUserDetails(userDetails);
        BankDetailResponse response = bankService.getBankDetail(user, bankId, search, sortBy, sortOrder, page, size);

        return ResponseEntity.ok(new SuccessResponse<>(response));
    }


    @PostMapping
    public ResponseEntity<SuccessResponse<BankResponse>> createBank(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody BankRequest request) {
        
        User user = getUserFromUserDetails(userDetails);
        BankResponse response = bankService.createBank(user, request);
        
        return ResponseEntity.ok(new SuccessResponse<>(
                0,
                "Банк успешно добавлен",
                response
        ));
    }

    @GetMapping
    public ResponseEntity<SuccessResponse<List<BankResponse>>> getAllBanks(
            @AuthenticationPrincipal UserDetails userDetails) {
        
        User user = getUserFromUserDetails(userDetails);
        List<BankResponse> banks = bankService.getAllBanks(user);
        
        return ResponseEntity.ok(new SuccessResponse<>(banks));
    }

    @PutMapping("/{bankId}")
    public ResponseEntity<SuccessResponse<BankResponse>> updateBank(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long bankId,
            @Valid @RequestBody BankRequest request) {
        
        User user = getUserFromUserDetails(userDetails);
        BankResponse response = bankService.updateBank(user, bankId, request);
        
        return ResponseEntity.ok(new SuccessResponse<>(
                0,
                "Банк успешно обновлен",
                response
        ));
    }

    @DeleteMapping("/{bankId}")
    public ResponseEntity<SuccessResponse<Void>> deleteBank(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long bankId) {
        
        User user = getUserFromUserDetails(userDetails);
        bankService.deleteBank(user, bankId);
        
        return ResponseEntity.ok(new SuccessResponse<>("Банк успешно удален"));
    }

    // Экспорт банков в Excel
    @GetMapping("/export")
    public ResponseEntity<byte[]> exportBanks(
            @AuthenticationPrincipal UserDetails userDetails) throws IOException {
        
        User user = getUserFromUserDetails(userDetails);
        byte[] excelData = bankService.exportBanksToExcel(user);
        
        String filename = URLEncoder.encode("banks.xlsx", StandardCharsets.UTF_8);
        
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(excelData);
    }

    // Импорт банков из Excel
    @PostMapping("/import")
    public ResponseEntity<SuccessResponse<BankImportResult>> importBanks(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam("file") MultipartFile file) throws IOException {
        
        User user = getUserFromUserDetails(userDetails);
        BankImportResult result = bankService.importBanksFromExcel(user, file);
        
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