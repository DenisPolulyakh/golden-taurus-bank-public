// Категориальные цвета типов дохода — фиксированный порядок, не циклится.
// Проверено scripts/validate_palette.js (skill dataviz): CVD-разделение и порог
// для нормального зрения пройдены для восьми слотов. OTHER — девятый, «хвостовой»
// тип по смыслу самого справочника («Прочее»), поэтому у него отдельный
// нейтральный серый вместо девятого сгенерированного оттенка.
export const INCOME_TYPE_COLORS = {
    ADVANCE: 'var(--chart-1)',
    SALARY: 'var(--chart-2)',
    DEPOSIT_INTEREST: 'var(--chart-3)',
    DIVIDENDS: 'var(--chart-4)',
    CASHBACK: 'var(--chart-5)',
    TAX_DEDUCTION: 'var(--chart-6)',
    WINDFALL: 'var(--chart-7)',
    GIFTS: 'var(--chart-8)',
    OTHER: 'var(--muted-foreground)',
}

// Тот же порядок, что у backend-enum IncomeType — легенда и стек всегда идут
// в одном порядке независимо от того, что пришло в ответе первым.
export const INCOME_TYPE_ORDER = [
    'ADVANCE', 'SALARY', 'DEPOSIT_INTEREST', 'DIVIDENDS',
    'CASHBACK', 'TAX_DEDUCTION', 'WINDFALL', 'GIFTS', 'OTHER',
]

export const incomeTypeColor = (code) => INCOME_TYPE_COLORS[code] || 'var(--muted-foreground)'
