import { createContext, useCallback, useContext, useEffect, useRef, useState } from 'react'
import api from '@/api/axios'
import {
    changePassphrase,
    createVault,
    findCard,
    openVault,
    openVaultWithKeys,
    removeCard,
    rotateKey,
    sealVault,
    upsertCard,
} from '@/lib/cardVault'
import { clearPin, hasPin, openWithPin, savePin } from '@/lib/vaultPin'

const AUTO_LOCK_MS = 10 * 60 * 1000

const CardVaultContext = createContext(null)

export function CardVaultProvider({ children }) {
    const [payload, setPayload] = useState(null)
    const [version, setVersion] = useState(null)
    const [loaded, setLoaded] = useState(false)
    const [vault, setVault] = useState(null)
    const [busy, setBusy] = useState(false)
    const [unlockOpen, setUnlockOpen] = useState(false)
    const [pinEnabled, setPinEnabled] = useState(hasPin())
    const lockTimerRef = useRef(null)

    const lock = useCallback(() => {
        if (lockTimerRef.current) {
            clearTimeout(lockTimerRef.current)
            lockTimerRef.current = null
        }
        setVault(null)
    }, [])

    const armAutoLock = useCallback(() => {
        if (lockTimerRef.current) {
            clearTimeout(lockTimerRef.current)
        }
        lockTimerRef.current = setTimeout(() => setVault(null), AUTO_LOCK_MS)
    }, [])

    const load = useCallback(async () => {
        const response = await api.get('/card-requisites')
        const data = response.data.data || {}

        setPayload(data.payload ?? null)
        setVersion(data.version ?? null)
        setLoaded(true)

        return data
    }, [])

    useEffect(() => {
        load().catch((error) => {
            console.error('Не удалось загрузить реквизиты:', error)
            setLoaded(true)
        })

        return () => {
            if (lockTimerRef.current) {
                clearTimeout(lockTimerRef.current)
            }
        }
    }, [load])

    const persist = useCallback(async (nextVault, currentVersion) => {
        const response = await api.put('/card-requisites', {
            payload: sealVault(nextVault),
            version: currentVersion,
        })
        const data = response.data.data

        setPayload(data.payload)
        setVersion(data.version)
        setVault(nextVault)
        armAutoLock()

        return nextVault
    }, [armAutoLock])

    const unlock = useCallback(async (passphrase) => {
        setBusy(true)
        try {
            const data = await load()
            if (!data.payload) {
                return persist(await createVault(passphrase), null)
            }

            const opened = await openVault(data.payload, passphrase)
            setVault(opened)
            armAutoLock()

            return opened
        } finally {
            setBusy(false)
        }
    }, [armAutoLock, load, persist])

    const syncPin = useCallback(() => {
        const actual = hasPin()
        setPinEnabled(actual)

        return actual
    }, [])

    const unlockWithPin = useCallback(async (pin) => {
        setBusy(true)
        try {
            const { kek, dek } = await openWithPin(pin)
            const data = await load()
            if (!data.payload) {
                throw new Error('Сундук на сервере пуст')
            }

            let opened
            try {
                opened = openVaultWithKeys(data.payload, kek, dek)
            } catch (error) {
                clearPin()
                setPinEnabled(false)
                throw error
            }

            setVault(opened)
            armAutoLock()

            return opened
        } catch (error) {
            setPinEnabled(hasPin())
            throw error
        } finally {
            setBusy(false)
        }
    }, [armAutoLock, load])

    const enablePin = useCallback(async (pin) => {
        await savePin(pin, vault.kek, vault.dek)
        setPinEnabled(true)
    }, [vault])

    const disablePin = useCallback(() => {
        clearPin()
        setPinEnabled(false)
    }, [])

    const rotateVaultKey = useCallback(async () => {
        setBusy(true)
        try {
            await persist(rotateKey(vault), version)
            clearPin()
            setPinEnabled(false)
        } finally {
            setBusy(false)
        }
    }, [persist, vault, version])

    const replacePassphrase = useCallback(async (passphrase) => {
        setBusy(true)
        try {
            await persist(await changePassphrase(vault, passphrase), version)
            clearPin()
            setPinEnabled(false)
        } finally {
            setBusy(false)
        }
    }, [persist, vault, version])

    const saveCard = useCallback(async (entry) => {
        setBusy(true)
        try {
            return await persist(upsertCard(vault, entry), version)
        } finally {
            setBusy(false)
        }
    }, [persist, vault, version])

    const dropCard = useCallback(async (cardId) => {
        setBusy(true)
        try {
            return await persist(removeCard(vault, cardId), version)
        } finally {
            setBusy(false)
        }
    }, [persist, vault, version])

    const value = {
        loaded,
        busy,
        exists: payload !== null,
        isOpen: vault !== null,
        cards: vault?.cards || [],
        cardById: (cardId) => findCard(vault, cardId),
        unlock,
        unlockWithPin,
        pinEnabled,
        syncPin,
        enablePin,
        disablePin,
        rotateVaultKey,
        replacePassphrase,
        lock,
        unlockOpen,
        setUnlockOpen,
        requestUnlock: () => setUnlockOpen(true),
        saveCard,
        dropCard,
        touch: armAutoLock,
    }

    return <CardVaultContext.Provider value={value}>{children}</CardVaultContext.Provider>
}

export function useCardVault() {
    const context = useContext(CardVaultContext)
    if (!context) {
        throw new Error('useCardVault вне CardVaultProvider')
    }

    return context
}
