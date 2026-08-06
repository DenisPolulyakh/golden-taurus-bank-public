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
public class VaultImportResult {
    private int vaultsAdded;
    private int vaultsSkipped;
    private int bullionNamesAdded;
    private int bullionNamesSkipped;
    private int bullionsAdded;
    
    @Builder.Default
    private List<String> addedVaults = new ArrayList<>();
    
    @Builder.Default
    private List<String> skippedVaults = new ArrayList<>();
    
    @Builder.Default
    private List<String> addedBullionNames = new ArrayList<>();
    
    @Builder.Default
    private List<String> errors = new ArrayList<>();
}