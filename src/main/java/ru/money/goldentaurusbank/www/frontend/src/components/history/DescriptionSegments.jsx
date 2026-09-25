export const DescriptionSegments = ({ transaction }) => {
    const segments = transaction.descriptionSegments
    if (Array.isArray(segments) && segments.length > 0) {
        // seg.archived — хранилища больше нет: цвет остаётся своим, но гаснет
        return (
            <>
                {segments.map((seg, i) => (
                    <span
                        key={i}
                        className={seg.archived ? 'opacity-50 line-through' : undefined}
                        title={seg.archived ? 'Хранилище удалено' : undefined}
                        style={seg.color ? { color: seg.color, fontWeight: 600 } : undefined}
                    >
                        {seg.text}
                    </span>
                ))}
            </>
        )
    }
    return transaction.description || transaction.comment || '-'
}

