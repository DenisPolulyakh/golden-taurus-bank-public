import { toast } from 'sonner'
import { formatAmount, toCents } from '@/lib/format'

/**
 * Тост о корректирующей операции после правки суммы слитка. Дельту считаем на клиенте:
 * старую сумму страница знает до отправки формы, а ответ PUT о проведённой операции молчит.
 * Полей о проведённой операции в ответе не планируется — см. PLAN_BULLION_AMOUNT_EDIT.md, п. 3.4.
 */
export const notifyAmountChange = (previousAmount, newAmount) => {
    const deltaCents = toCents(newAmount) - toCents(previousAmount)

    if (deltaCents > 0) {
        toast.success(`Проведено пополнение на ${formatAmount(deltaCents / 100)} ₽`)
    } else if (deltaCents < 0) {
        toast.success(`Проведено снятие на ${formatAmount(-deltaCents / 100)} ₽`)
    } else {
        toast.success('Слиток обновлён')
    }
}

/**
 * Пустой слиток — тот, у которого на балансе ноль. Удаляется он начисто:
 * переносить нечего, операции в истории не появляется.
 * Неизвестная сумма (null/undefined) пустой не считается — иначе слиток с
 * остатком удалился бы мимо переноса.
 */
export const isEmptyBullionAmount = (amount) => amount != null && Number(amount) === 0
