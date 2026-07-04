import { useCallback } from 'react';

export const useAmountFormat = () => {
    const formatAmount = useCallback((amount) => {
        if (amount === null || amount === undefined) return '0';
        const num = typeof amount === 'string' ? parseFloat(amount) : amount;
        if (isNaN(num)) return '0';
        return num.toLocaleString('ru-RU', {
            minimumFractionDigits: 0,
            maximumFractionDigits: 2
        });
    }, []);

    const parseAmount = useCallback((formattedAmount) => {
        if (!formattedAmount) return 0;
        const cleanValue = formattedAmount.replace(/\s/g, '').replace(',', '.');
        const number = parseFloat(cleanValue);
        return isNaN(number) ? 0 : Math.round(number * 100) / 100;
    }, []);

    return { formatAmount, parseAmount };
};

export default useAmountFormat;