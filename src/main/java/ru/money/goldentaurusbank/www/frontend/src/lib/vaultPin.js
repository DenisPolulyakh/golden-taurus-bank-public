import { getUserKey } from '@/api/axios'
import { deriveKek, fromBase64, randomBytes, seal, toBase64, unseal } from './cardVault'

const STORAGE_PREFIX = 'card-vault-pin'
const PIN_KDF = { alg: 'argon2id', m: 65536, t: 3, p: 1 }
const MAX_ATTEMPTS = 5
const KEY_BYTES = 32

export const PIN_LENGTH = 4
export const WRONG_PIN = 'WRONG_PIN'
export const PIN_BLOCKED = 'PIN_BLOCKED'
export const NO_PIN = 'NO_PIN'

function storageKey() {
    return `${STORAGE_PREFIX}:${getUserKey()}`
}

function read() {
    try {
        const raw = localStorage.getItem(storageKey())
        return raw ? JSON.parse(raw) : null
    } catch (error) {
        return null
    }
}

function write(record) {
    try {
        localStorage.setItem(storageKey(), JSON.stringify(record))
    } catch (error) {
        console.error('Не удалось сохранить пин:', error)
    }
}

export function hasPin() {
    return read() !== null
}

export function clearPin() {
    try {
        localStorage.removeItem(storageKey())
    } catch (error) {
        console.error('Не удалось удалить пин:', error)
    }
}

export async function savePin(pin, kek, dek) {
    const salt = randomBytes(16)
    const key = await deriveKek(pin, PIN_KDF, salt)

    const secret = new Uint8Array(KEY_BYTES * 2)
    secret.set(kek, 0)
    secret.set(dek, KEY_BYTES)

    write({
        v: 1,
        kdf: { ...PIN_KDF, salt: toBase64(salt) },
        wrap: seal(key, secret),
        attempts: 0,
    })
}

export async function openWithPin(pin) {
    const record = read()
    if (!record) {
        const missing = new Error('Пин не сохранён на этом компьютере')
        missing.code = NO_PIN
        throw missing
    }

    const key = await deriveKek(pin, record.kdf, fromBase64(record.kdf.salt))

    let secret
    try {
        secret = unseal(key, record.wrap)
    } catch (error) {
        const attempts = (record.attempts || 0) + 1
        if (attempts >= MAX_ATTEMPTS) {
            clearPin()
            const blocked = new Error('Пин стёрт после пяти неверных попыток')
            blocked.code = PIN_BLOCKED
            throw blocked
        }

        write({ ...record, attempts })
        const wrong = new Error('Неверный пин')
        wrong.code = WRONG_PIN
        wrong.attemptsLeft = MAX_ATTEMPTS - attempts
        throw wrong
    }

    if (record.attempts) {
        write({ ...record, attempts: 0 })
    }

    return { kek: secret.slice(0, KEY_BYTES), dek: secret.slice(KEY_BYTES) }
}
