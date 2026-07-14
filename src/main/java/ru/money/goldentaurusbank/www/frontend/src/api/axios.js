import axios from 'axios';

// Определяем базовый URL автоматически
const getBaseUrl = () => {
    // Если запущено на localhost или 127.0.0.1
    if (window.location.hostname === 'localhost' || window.location.hostname === '127.0.0.1') {
        return '/api';  // Используем прокси Vite
    }

    // Если запущено на любом IP адресе (телефон, другой компьютер в сети)
    // Берем текущий IP и подставляем порт бэкенда
    return `http://${window.location.hostname}:8080/api`;
};

const api = axios.create({
    baseURL: getBaseUrl(),
    headers: {
        'Content-Type': 'application/json',
    },
    withCredentials: true,
});

// Access token только в памяти
let accessToken = null;

// Таймер упреждающего обновления токена
let refreshTimerId = null;
// За сколько до истечения токена запускаем обновление (60 секунд)
const REFRESH_LEEWAY_MS = 60 * 1000;

// Декодируем payload JWT, чтобы узнать время истечения (exp)
const getTokenExpiryMs = (token) => {
    try {
        const payload = token.split('.')[1];
        const base64 = payload.replace(/-/g, '+').replace(/_/g, '/');
        const json = decodeURIComponent(
            atob(base64)
                .split('')
                .map((c) => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2))
                .join('')
        );
        const { exp } = JSON.parse(json);
        return exp ? exp * 1000 : null;
    } catch (e) {
        console.error('Не удалось декодировать токен:', e);
        return null;
    }
};

// Планируем обновление токена заранее, до его истечения
const scheduleTokenRefresh = (token) => {
    if (refreshTimerId) {
        clearTimeout(refreshTimerId);
        refreshTimerId = null;
    }

    const expiryMs = getTokenExpiryMs(token);
    if (!expiryMs) return;

    // Запускаем обновление за REFRESH_LEEWAY_MS до истечения (но не раньше чем через 1с)
    const delay = Math.max(expiryMs - Date.now() - REFRESH_LEEWAY_MS, 1000);

    refreshTimerId = setTimeout(() => {
        // Обновляем токен в фоне; ошибку глотаем — реактивный перехватчик подхватит при следующем запросе
        refreshAccessToken().catch(() => {});
    }, delay);
};

export const setAccessToken = (token) => {
    accessToken = token;
    if (token) {
        scheduleTokenRefresh(token);
    }
};

export const getAccessToken = () => {
    return accessToken;
};

export const clearAccessToken = () => {
    accessToken = null;
    if (refreshTimerId) {
        clearTimeout(refreshTimerId);
        refreshTimerId = null;
    }
};

// Токен считается «почти истёкшим», если до его конца осталось меньше запаса
const isTokenExpiringSoon = (token) => {
    const expiryMs = getTokenExpiryMs(token);
    if (!expiryMs) return true;
    return expiryMs - Date.now() <= REFRESH_LEEWAY_MS;
};

// Единый промис обновления токена — общий для упреждающего таймера и реактивного перехватчика
let refreshPromise = null;

const refreshAccessToken = () => {
    // Если обновление уже идёт — переиспользуем тот же промис
    if (refreshPromise) {
        return refreshPromise;
    }

    refreshPromise = api
        .post('/auth/refresh', null, { _skipAuthRefresh: true })
        .then((response) => {
            const newToken = response.data.data.token;
            setAccessToken(newToken); // также перепланирует следующий refresh
            return newToken;
        })
        .finally(() => {
            refreshPromise = null;
        });

    return refreshPromise;
};

// Перехватчик запросов
api.interceptors.request.use(
    (config) => {
        if (accessToken) {
            config.headers.Authorization = `Bearer ${accessToken}`;
        }
        return config;
    },
    (error) => Promise.reject(error)
);

// Перехватчик ответов: реактивное обновление токена как страховка
api.interceptors.response.use(
    (response) => response,
    async (error) => {
        const originalRequest = error.config;

        // Не пытаемся обновлять токен для самого запроса обновления или после повторной попытки
        if (
            error.response?.status === 401 &&
            originalRequest &&
            !originalRequest._retry &&
            !originalRequest._skipAuthRefresh
        ) {
            originalRequest._retry = true;

            try {
                const newToken = await refreshAccessToken();
                originalRequest.headers.Authorization = `Bearer ${newToken}`;
                return api(originalRequest);
            } catch (refreshError) {
                clearAccessToken();
                window.location.href = '/login';
                return Promise.reject(refreshError);
            }
        }

        return Promise.reject(error);
    }
);

// Обработка «пробуждения» вкладки (например, после сна ноутбука):
// таймеры setTimeout во время сна не срабатывают вовремя, поэтому при
// возвращении фокуса проверяем токен и при необходимости обновляем сразу.
const refreshIfNeeded = () => {
    if (!accessToken) return;

    if (isTokenExpiringSoon(accessToken)) {
        // Токен уже истёк или вот-вот истечёт — обновляем немедленно
        refreshAccessToken().catch(() => {});
    } else {
        // Токен ещё валиден, но таймер мог сбиться за время сна — перепланируем
        scheduleTokenRefresh(accessToken);
    }
};

if (typeof document !== 'undefined') {
    document.addEventListener('visibilitychange', () => {
        if (document.visibilityState === 'visible') {
            refreshIfNeeded();
        }
    });
}

if (typeof window !== 'undefined') {
    window.addEventListener('focus', refreshIfNeeded);
}

export default api;
