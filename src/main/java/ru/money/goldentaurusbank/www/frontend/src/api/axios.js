import axios from 'axios';
import { toast } from 'sonner';

// Базовый URL всегда относительный: '/api'.
const getBaseUrl = () => {
    return '/api';
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

// Показываем всплывающее окно с ошибкой бэкенда (единая точка для всего приложения).
// Текст берём из ResponseCodes-сообщения (err.response.data.message).
const showErrorToast = (error) => {
    const data = error.response?.data;
    let message;

    if (data?.message) {
        // Единый формат ошибок бэка: { code, message, details? }
        message = data.message;
    } else if (error.response) {
        // Ответ есть, но без нашего message (например, 404/500 без тела)
        message = `Ошибка сервера (${error.response.status})`;
    } else if (error.request) {
        // Запрос ушёл, но ответа нет — сеть недоступна / сервер лежит
        message = 'Сервер недоступен. Проверьте соединение.';
    } else {
        message = error.message || 'Неизвестная ошибка';
    }

    toast.error(message);
};

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

        // Централизованный тост об ошибке.
        if (originalRequest && !originalRequest._skipErrorToast && !originalRequest._skipAuthRefresh) {
            showErrorToast(error);
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
