/**
 * Печать типа слитка: зелёная «Дебетовый», красная «Кредитный».
 *
 * Рисуется инлайновым SVG, а не картинкой: печать меняет цвет и текст,
 * а картинку пришлось бы держать двумя файлами и тянуть отдельным запросом.
 */
const STAMPS = {
    DEBIT: { label: 'Дебетовый', color: 'var(--success)' },
    CREDIT: { label: 'Кредитный', color: 'var(--destructive)' }
};

function BullionTypeStamp({ type }) {
    // Тип пришёл позже самих слитков: у старых записей его может не быть
    const stamp = STAMPS[type] || STAMPS.DEBIT;

    return (
        // Размеры пришли из .bullion-stamp в удалённом Bullions.css
        <svg
            className="h-[30px] w-[98px] shrink-0"
            viewBox="0 0 150 46"
            role="img"
            aria-label={`Тип слитка: ${stamp.label.toLowerCase()}`}
        >
            <title>{stamp.label} слиток</title>
            <g fill="none" stroke={stamp.color} opacity="0.85">
                <rect x="4" y="4" width="142" height="38" rx="8" strokeWidth="3" />
                <rect x="10" y="10" width="130" height="26" rx="5" strokeWidth="1" strokeDasharray="4 3" />
            </g>
            <text
                x="75"
                y="24"
                fill={stamp.color}
                opacity="0.85"
                textAnchor="middle"
                dominantBaseline="central"
                fontSize="15"
                fontWeight="700"
                letterSpacing="1.5"
                fontFamily="inherit"
            >
                {stamp.label.toUpperCase()}
            </text>
        </svg>
    );
}

export default BullionTypeStamp;
