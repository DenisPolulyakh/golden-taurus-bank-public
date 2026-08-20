package ru.money.goldentaurusbank.www.backend.model.dto.enums;

import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.Arrays;

@Getter
public enum ResponseCodes {

    // Успех (0xxx)
    SUCCESS(0, "Успешно", HttpStatus.OK),

    // Ошибки аутентификации (1xxx)
    NO_ACTIVE_ACCOUNT(1001, "Аккаунт не активирован. Подтвердите email, перейдя по ссылке в письме.", HttpStatus.FORBIDDEN),
    WRONG_CREDENTIALS(1002, "Неверный email или пароль", HttpStatus.UNAUTHORIZED),
    USER_NOT_FOUND(1003, "Пользователь с таким email не найден", HttpStatus.NOT_FOUND),
    EMAIL_ALREADY_EXISTS(1004, "Пользователь с таким email уже существует", HttpStatus.CONFLICT),
    INVALID_VERIFICATION_TOKEN(1005, "Неверный или просроченный токен верификации", HttpStatus.BAD_REQUEST),
    EMAIL_ALREADY_VERIFIED(1006, "Email уже подтверждён", HttpStatus.BAD_REQUEST),
    ACCOUNT_BLOCKED(1007, "Ваш аккаунт заблокирован", HttpStatus.FORBIDDEN),
    EMAIL_TRUST(1008, "Подтвердите email перед входом. Проверьте почту.", HttpStatus.FORBIDDEN),


    // Ошибки валидации (2xxx)
    VALIDATION_ERROR(2001, "Ошибка валидации", HttpStatus.BAD_REQUEST),

    // Ошибки доступа (3xxx)
    ACCESS_DENIED(3001, "Доступ запрещён", HttpStatus.FORBIDDEN),
    UNAUTHORIZED(3002, "Требуется авторизация", HttpStatus.UNAUTHORIZED),
    BULLION_ACCESS_DENIED(3003, "Нельзя перемещать слитки между разными пользователями", HttpStatus.FORBIDDEN),

    // Бизнес-ошибки (4xxx)
    CANNOT_TRANSFER_TO_SAME_VAULT(4000, "Нельзя переместить слиток в то же хранилище", HttpStatus.BAD_REQUEST),
    TRANSACTION_NOT_FOUND(4001, "Транзакция не найдена", HttpStatus.NOT_FOUND),
    INSUFFICIENT_FUNDS(4002, "Недостаточно средств", HttpStatus.BAD_REQUEST),
    BULLION_NAME_NOT_FOUND(4003, "Наименование слитка не найдено", HttpStatus.BAD_REQUEST),
    VAULT_NOT_FOUND(4004, "Хранилище не найдено", HttpStatus.BAD_REQUEST),
    VAULT_ALREADY_EXISTS(4005, "Банк с таким названием уже существует", HttpStatus.CONFLICT),
    BULLION_NOT_FOUND(4006,"Слиток не найден", HttpStatus.BAD_REQUEST),
    BANK_NOT_FOUND(4007,"Банк не найден", HttpStatus.BAD_REQUEST),
    CHANGE_AMOUNT_ZERO(4008, "Cумма пополнения или снятия должна отличаться от 0", HttpStatus.BAD_REQUEST),
    VAULT_NOT_SET(4009, "Хранилище для переноса не задано", HttpStatus.BAD_REQUEST),
    INVALID_COLOR(4010,"Недопустимый цвет. Выберите цвет из предложенных", HttpStatus.BAD_REQUEST),
    COLOR_ALREADY_USE(4011,"Этот цвет уже используется в другом наименовании", HttpStatus.BAD_REQUEST),
    BULLION_NAME_LINKED(4012, "Наименование привязано к слитку, сначала удалите слиток",HttpStatus.BAD_REQUEST),
    TRANSACTION_ALREADY_REVERSED(4013, "Транзакция уже откачена", HttpStatus.BAD_REQUEST),
    LIQUIDITY_RESERVE_NOT_EMPTY(4014, "В ликвидном хранилище есть слитки с остатком. Сначала перенесите их в другое хранилище", HttpStatus.BAD_REQUEST),
    VAULT_INCOME_NOT_ALLOWED(4015, "В это хранилище вносить нельзя: снята галочка «Можно вносить»", HttpStatus.BAD_REQUEST),
    VAULT_EXPENSE_NOT_ALLOWED(4016, "Из этого хранилища снимать нельзя: снята галочка «Можно снимать»", HttpStatus.BAD_REQUEST),
    VAULT_TRANSFER_NOT_ALLOWED(4017, "Это хранилище не участвует в переводах: снята галочка «Можно переводить»", HttpStatus.BAD_REQUEST),
    BANK_LINKED(4018, "Банк привязан к хранилищу, сначала удалите хранилище", HttpStatus.BAD_REQUEST),
    CREDIT_CARD_NOT_FOUND(4019, "Кредитная карта не найдена", HttpStatus.BAD_REQUEST),
    CREDIT_CARD_LIMIT_EXCEEDED(4020, "Списание превышает доступный лимит карты", HttpStatus.BAD_REQUEST),
    CREDIT_CARD_REPAY_EXCEEDS_DEBT(4021, "Погашение больше текущей задолженности", HttpStatus.BAD_REQUEST),
    CREDIT_CARD_ALREADY_EXISTS(4022, "Карта с таким названием уже существует", HttpStatus.CONFLICT),
    CREDIT_CARD_INVALID_NUMBER(4023, "Номер карты должен состоять из 12–19 цифр", HttpStatus.BAD_REQUEST),
    BULLION_NOT_CREDIT(4024, "В накопитель можно добавить только кредитный слиток", HttpStatus.BAD_REQUEST),
    BULLION_ALREADY_LINKED(4025, "Слиток уже привязан к другой карте", HttpStatus.BAD_REQUEST),
    CREDIT_CARD_HISTORY_NOT_FOUND(4026, "Операция по карте не найдена", HttpStatus.NOT_FOUND),
    CREDIT_CARD_OPERATION_ALREADY_REVERSED(4027, "Операция по карте уже откачена", HttpStatus.BAD_REQUEST),
    BULLION_NOT_ACCUMULATOR(4028, "Слиток не привязан к кредитной карте", HttpStatus.BAD_REQUEST),
    TRANSACTION_LOCKED_BY_CARD(4029, "Операция входит в погашение по карте — откатывайте её из истории карты", HttpStatus.BAD_REQUEST),
    CREDIT_CARD_LIMIT_BELOW_DEBT(4030, "Лимит не может быть меньше задолженности", HttpStatus.BAD_REQUEST),


    // Системные ошибки (5xxx)
    INTERNAL_ERROR(5000, "Внутренняя ошибка сервера", HttpStatus.INTERNAL_SERVER_ERROR),
    EMAIL_SEND_ERROR(5001, "Не удалось отправить письмо", HttpStatus.INTERNAL_SERVER_ERROR);

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;

    ResponseCodes(int code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    public static ResponseCodes getByCode(int code) {
        return Arrays.stream(ResponseCodes.values()).filter(v -> v.getCode() == code).findFirst().orElse(INTERNAL_ERROR);
    }
}