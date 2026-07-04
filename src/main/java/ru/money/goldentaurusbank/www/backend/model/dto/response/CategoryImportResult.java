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
public class CategoryImportResult {
    private int totalProcessed;
    private int added;
    private int skipped;
    
    @Builder.Default
    private List<String> errors = new ArrayList<>();
    
    @Builder.Default
    private List<String> skippedCategories = new ArrayList<>();
    
    @Builder.Default
    private List<String> addedCategories = new ArrayList<>();
}