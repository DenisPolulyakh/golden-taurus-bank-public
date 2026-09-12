import { openVault, WRONG_PASSPHRASE } from '@/lib/cardVault'
import { buildSheetHtml } from '@/lib/vaultSheet'

const dropZone = document.getElementById('drop')
const fileInput = document.getElementById('file')
const textInput = document.getElementById('text')
const phraseInput = document.getElementById('phrase')
const openButton = document.getElementById('open')
const statusBox = document.getElementById('status')
const errorBox = document.getElementById('error')
const inputPanel = document.getElementById('input-panel')
const resultPanel = document.getElementById('result-panel')
const sheetFrame = document.getElementById('sheet')
const printButton = document.getElementById('print')
const resetButton = document.getElementById('reset')

function setError(message) {
    errorBox.textContent = message || ''
}

function setStatus(message) {
    statusBox.textContent = message || ''
}

function parseInput(raw) {
    const text = raw.trim()
    if (!text) {
        throw new Error('Сначала загрузите пакет или вставьте сундук')
    }

    let parsed
    try {
        parsed = JSON.parse(text)
    } catch (error) {
        throw new Error('Это не похоже ни на пакет, ни на сундук')
    }

    const pack = parsed.vault ? parsed : parsed.data && parsed.data.vault ? parsed.data : null
    if (pack && pack.vault && pack.vault.payload) {
        return { payload: pack.vault.payload, cards: pack.cards || [], generatedAt: pack.generatedAt }
    }

    if (parsed.kdf && parsed.data && parsed.wrap) {
        return { payload: text, cards: [], generatedAt: null }
    }

    throw new Error('В файле нет сундука')
}

function toSheetCards(cards) {
    return cards.map((card) => ({
        id: card.cardId,
        name: card.name,
        last4: card.last4,
        maskedNumber: card.last4 ? `•••• ${card.last4}` : '',
        debt: card.debt,
        limit: card.limit,
        gracePeriodDate: card.gracePeriodDate,
    }))
}

async function handleOpen() {
    setError('')
    openButton.disabled = true
    setStatus('Расшифровываем, это пара секунд...')

    try {
        const source = parseInput(textInput.value)
        const vault = await openVault(source.payload, phraseInput.value)

        sheetFrame.srcdoc = buildSheetHtml({
            cards: toSheetCards(source.cards),
            entries: vault.cards,
            generatedAt: source.generatedAt,
        })

        inputPanel.classList.add('hidden')
        resultPanel.classList.remove('hidden')
        phraseInput.value = ''
    } catch (error) {
        setError(error.code === WRONG_PASSPHRASE ? 'Фраза не подходит' : error.message)
    } finally {
        openButton.disabled = false
        setStatus('')
    }
}

function loadFile(file) {
    const reader = new FileReader()
    reader.onload = () => {
        textInput.value = String(reader.result || '')
        setError('')
        setStatus(`Загружен файл: ${file.name}`)
    }
    reader.onerror = () => setError('Не удалось прочитать файл')
    reader.readAsText(file)
}

dropZone.addEventListener('click', () => fileInput.click())
fileInput.addEventListener('change', () => {
    if (fileInput.files && fileInput.files[0]) {
        loadFile(fileInput.files[0])
    }
})

;['dragenter', 'dragover'].forEach((event) =>
    dropZone.addEventListener(event, (e) => {
        e.preventDefault()
        dropZone.classList.add('over')
    })
)

;['dragleave', 'drop'].forEach((event) =>
    dropZone.addEventListener(event, (e) => {
        e.preventDefault()
        dropZone.classList.remove('over')
    })
)

dropZone.addEventListener('drop', (e) => {
    const file = e.dataTransfer && e.dataTransfer.files && e.dataTransfer.files[0]
    if (file) {
        loadFile(file)
    }
})

openButton.addEventListener('click', handleOpen)
phraseInput.addEventListener('keydown', (e) => {
    if (e.key === 'Enter') {
        handleOpen()
    }
})

printButton.addEventListener('click', () => {
    sheetFrame.contentWindow.focus()
    sheetFrame.contentWindow.print()
})

resetButton.addEventListener('click', () => {
    sheetFrame.srcdoc = ''
    textInput.value = ''
    phraseInput.value = ''
    fileInput.value = ''
    setStatus('')
    resultPanel.classList.add('hidden')
    inputPanel.classList.remove('hidden')
})
