package ru.money.goldentaurusbank.www.backend.infrastructure.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import ru.money.goldentaurusbank.www.backend.infrastructure.exception.ApplicationException;
import ru.money.goldentaurusbank.www.backend.model.domain.User;
import ru.money.goldentaurusbank.www.backend.model.dto.request.VaultRequest;
import ru.money.goldentaurusbank.www.backend.model.dto.response.*;
import ru.money.goldentaurusbank.www.backend.repository.UserRepository;
import ru.money.goldentaurusbank.www.backend.service.VaultImportService;
import ru.money.goldentaurusbank.www.backend.service.VaultService;

import java.io.IOException;

import static ru.money.goldentaurusbank.www.backend.model.dto.enums.ResponseCodes.USER_NOT_FOUND;

@RestController
@RequestMapping("/api/vaults")
@RequiredArgsConstructor
public class VaultController {

    private final VaultService vaultService;
    private final UserRepository userRepository;
    private final VaultImportService vaultImportService;

    @PostMapping
    public ResponseEntity<SuccessResponse<VaultResponse>> createVault(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody VaultRequest request) {
        
        User user = getUserFromUserDetails(userDetails);
        VaultResponse response = vaultService.createVaultAndGetResponse(user, request);
        
        return ResponseEntity.ok(new SuccessResponse<>(
                0,
                "Хранилище успешно добавлено",
                response
        ));
    }

    @GetMapping
    public ResponseEntity<SuccessResponse<PageResponse<VaultResponse>>> getAllVaults(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortOrder,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        
        User user = getUserFromUserDetails(userDetails);
        PageResponse<VaultResponse> vaults = vaultService.getAllVaults(user, search, sortBy, sortOrder, page, size);
        
        return ResponseEntity.ok(new SuccessResponse<>(vaults));
    }

    @GetMapping("/{vaultId}")
    public ResponseEntity<SuccessResponse<VaultResponse>> getVaultById(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long vaultId) {

        User user = getUserFromUserDetails(userDetails);
        VaultResponse response = vaultService.getVaultByIdAndUser(user, vaultId);

        return ResponseEntity.ok(new SuccessResponse<>(response));
    }

    @PutMapping("/{vaultId}")
    public ResponseEntity<SuccessResponse<VaultResponse>> updateVault(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long vaultId,
            @Valid @RequestBody VaultRequest request) {
        
        User user = getUserFromUserDetails(userDetails);
        VaultResponse response = vaultService.updateVaultAndGetResponse(user, vaultId, request);
        
        return ResponseEntity.ok(new SuccessResponse<>(
                0,
                "Хранилище успешно обновлено",
                response
        ));
    }

    @DeleteMapping("/{vaultId}")
    public ResponseEntity<SuccessResponse<Void>> deleteVault(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long vaultId) {
        
        User user = getUserFromUserDetails(userDetails);
        vaultService.deleteVault(user, vaultId);
        
        return ResponseEntity.ok(new SuccessResponse<>("Хранилище успешно удалено"));
    }


    @GetMapping("/{vaultId}/summary")
    public ResponseEntity<SuccessResponse<VaultSummaryResponse>> getVaultSummary(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long vaultId) {

        User user = getUserFromUserDetails(userDetails);
        VaultSummaryResponse summary = vaultService.getVaultSummary(user, vaultId);

        return ResponseEntity.ok(new SuccessResponse<>(summary));
    }


    @PostMapping("/import")
    public ResponseEntity<SuccessResponse<VaultImportResult>> importVaults(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam("file") MultipartFile file) throws IOException {

        User user = getUserFromUserDetails(userDetails);
        VaultImportResult result = vaultImportService.importVaultsFromExcel(user, file);

        String message = String.format("Импорт завершен. Хранилищ добавлено: %d, Категорий добавлено: %d, Слитков добавлено: %d",
                result.getVaultsAdded(), result.getBullionNamesAdded(), result.getBullionsAdded());

        if (!result.getErrors().isEmpty()) {
            message += ". Ошибок: " + result.getErrors().size();
        }

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