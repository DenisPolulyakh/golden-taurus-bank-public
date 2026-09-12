import { argon2id } from 'hash-wasm'
import { gcm } from '@noble/ciphers/aes'

export const VAULT_VERSION = 1
export const WRONG_PASSPHRASE = 'WRONG_PASSPHRASE'

const KDF_DEFAULTS = { alg: 'argon2id', m: 131072, t: 3, p: 1 }
const SALT_BYTES = 16
const NONCE_BYTES = 12
const KEY_BYTES = 32

const textEncoder = new TextEncoder()
const textDecoder = new TextDecoder()

export function randomBytes(size) {
    const bytes = new Uint8Array(size)
    crypto.getRandomValues(bytes)
    return bytes
}

export function toBase64(bytes) {
    let binary = ''
    for (let i = 0; i < bytes.length; i += 1) {
        binary += String.fromCharCode(bytes[i])
    }
    return btoa(binary)
}

export function fromBase64(value) {
    const binary = atob(value)
    const bytes = new Uint8Array(binary.length)
    for (let i = 0; i < binary.length; i += 1) {
        bytes[i] = binary.charCodeAt(i)
    }
    return bytes
}

function wrongPassphrase() {
    const error = new Error('Фраза не подходит')
    error.code = WRONG_PASSPHRASE
    return error
}

export async function deriveKek(passphrase, kdf, salt) {
    return argon2id({
        password: passphrase,
        salt,
        parallelism: kdf.p,
        iterations: kdf.t,
        memorySize: kdf.m,
        hashLength: KEY_BYTES,
        outputType: 'binary',
    })
}

export function seal(key, plainBytes) {
    const nonce = randomBytes(NONCE_BYTES)
    return {
        alg: 'AES-256-GCM',
        nonce: toBase64(nonce),
        ct: toBase64(gcm(key, nonce).encrypt(plainBytes)),
    }
}

export function unseal(key, box) {
    return gcm(key, fromBase64(box.nonce)).decrypt(fromBase64(box.ct))
}

export async function createVault(passphrase) {
    const kdf = { ...KDF_DEFAULTS }
    const salt = randomBytes(SALT_BYTES)
    const kek = await deriveKek(passphrase, kdf, salt)

    return { kdf, salt, kek, dek: randomBytes(KEY_BYTES), cards: [] }
}

export async function openVault(payload, passphrase) {
    const envelope = JSON.parse(payload)
    const kdf = { alg: envelope.kdf.alg, m: envelope.kdf.m, t: envelope.kdf.t, p: envelope.kdf.p }
    const salt = fromBase64(envelope.kdf.salt)
    const kek = await deriveKek(passphrase, kdf, salt)

    try {
        const dek = unseal(kek, envelope.wrap)
        const opened = JSON.parse(textDecoder.decode(unseal(dek, envelope.data)))

        return { kdf, salt, kek, dek, cards: opened.cards || [] }
    } catch (error) {
        throw wrongPassphrase()
    }
}

export function openVaultWithKeys(payload, kek, dek) {
    const envelope = JSON.parse(payload)

    try {
        const opened = JSON.parse(textDecoder.decode(unseal(dek, envelope.data)))

        return {
            kdf: { alg: envelope.kdf.alg, m: envelope.kdf.m, t: envelope.kdf.t, p: envelope.kdf.p },
            salt: fromBase64(envelope.kdf.salt),
            kek,
            dek,
            cards: opened.cards || [],
        }
    } catch (error) {
        throw wrongPassphrase()
    }
}

export function sealVault(vault) {
    const data = seal(
        vault.dek,
        textEncoder.encode(JSON.stringify({ v: VAULT_VERSION, cards: vault.cards }))
    )

    return JSON.stringify({
        v: VAULT_VERSION,
        kdf: { ...vault.kdf, salt: toBase64(vault.salt) },
        wrap: seal(vault.kek, vault.dek),
        data,
    })
}

export async function changePassphrase(vault, passphrase) {
    const kdf = { ...KDF_DEFAULTS }
    const salt = randomBytes(SALT_BYTES)
    const kek = await deriveKek(passphrase, kdf, salt)

    return { ...vault, kdf, salt, kek }
}

export function rotateKey(vault) {
    return { ...vault, dek: randomBytes(KEY_BYTES) }
}

export function findCard(vault, cardId) {
    return vault?.cards.find((card) => card.cardId === cardId) || null
}

export function upsertCard(vault, entry) {
    const rest = vault.cards.filter((card) => card.cardId !== entry.cardId)

    return { ...vault, cards: [...rest, entry].sort((a, b) => a.cardId - b.cardId) }
}

export function removeCard(vault, cardId) {
    return { ...vault, cards: vault.cards.filter((card) => card.cardId !== cardId) }
}
