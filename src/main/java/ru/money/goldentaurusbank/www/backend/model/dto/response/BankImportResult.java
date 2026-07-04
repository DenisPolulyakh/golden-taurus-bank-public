package ru.money.goldentaurusbank.www.backend.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BankImportResult {
    private int totalProcessed;
    private int added;
    private int skipped;
    
    @Builder.Default
    private List<String> errors = new ArrayList<>();
    
    @Builder.Default
    private List<String> skippedBanks = new ArrayList<>();
    
    @Builder.Default
    private List<String> addedBanks = new ArrayList<>();
}