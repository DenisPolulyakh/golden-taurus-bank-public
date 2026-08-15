import { toast } from 'sonner';

/** Формат сумм как на страницах слитков: разряды пробелами, нулевые копейки не показываем. */
export const formatAmount = (amount) => {
    if (!amount && amount !== 0) return '0';
    const num = typeof amount === 'string' ? parseFloat(amount) : amount;
    if (isNaN(num)) return '0';

    const parts = num.toFixed(2).split('.');
    const formattedInteger = parts[0].replace(/\B(?=(\d{3})+(?!\d))/g, ' ');
    const trimmedDecimal = parts[1].replace(/0+$/, '');

    return trimmedDecimal ? `${formattedInteger}.${trimmedDecimal}` : formattedInteger;
};

/** Копейки целым числом: сравнивать суммы во float нельзя, 100.10 - 100.05 даст хвост. */
export const toCents = (value) => {
    const num = typeof value === 'string' ? parseFloat(value) : value;
    return isNaN(num) ? 0 : Math.round(num * 100);
};

/**
 * Тост о корректирующей операции после правки суммы слитка. Дельту считаем на клиенте:
 * старую сумму страница знает до отправки формы, а ответ PUT о проведённой операции молчит.
 * Когда приедет Ф2 из PLAN_VAULT_FLAGS — переключить на appliedOperation/appliedAmount из ответа.
 */
export const notifyAmountChange = (previousAmount, newAmount) => {
    const deltaCents = toCents(newAmount) - toCents(previousAmount);

    if (deltaCents > 0) {
        toast.success(`Проведено пополнение на ${formatAmount(deltaCents / 100)} ₽`);
    } else if (deltaCents < 0) {
        toast.success(`Проведено снятие на ${formatAmount(-deltaCents / 100)} ₽`);
    } else {
        toast.success('Слиток обновлён');
    }
};
