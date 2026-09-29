import { useEffect, useRef, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'

const HIGHLIGHT_MS = 2000

export function useArrivalHighlight(stateKey, ready, elementIdPrefix) {
    const location = useLocation()
    const navigate = useNavigate()
    const [highlightedId, setHighlightedId] = useState(null)
    const timerRef = useRef(null)

    useEffect(() => {
        const target = location.state?.[stateKey]
        if (!ready || target == null) return

        setHighlightedId(target)

        const rest = { ...location.state }
        delete rest[stateKey]
        navigate(location.pathname + location.search, { replace: true, state: rest })

        requestAnimationFrame(() => {
            document
                .getElementById(`${elementIdPrefix}-${target}`)
                ?.scrollIntoView({ block: 'center', behavior: 'smooth' })
        })

        clearTimeout(timerRef.current)
        timerRef.current = setTimeout(() => setHighlightedId(null), HIGHLIGHT_MS)
    }, [ready, location, stateKey, elementIdPrefix, navigate])

    useEffect(() => () => clearTimeout(timerRef.current), [])

    return highlightedId
}
