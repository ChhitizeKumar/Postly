import axios, { AxiosInstance, InternalAxiosRequestConfig, AxiosResponse } from 'axios'
import { useAuthStore } from '@/store/authStore'

const BASE_URL = '/api/v1'

// ============================================================
// Axios instance - all requests go through here
// ============================================================
export const apiClient: AxiosInstance = axios.create({
  baseURL: BASE_URL,
  timeout: 15000,
  headers: {
    'Content-Type': 'application/json',
  },
})

// ============================================================
// Request interceptor - attach JWT to every request
// ============================================================
apiClient.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const token = useAuthStore.getState().accessToken
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

// ============================================================
// Response interceptor - handle 401 and auto-refresh token
// ============================================================
let isRefreshing = false
let refreshSubscribers: Array<(token: string) => void> = []

const onRefreshed = (token: string) => {
  refreshSubscribers.forEach((cb) => cb(token))
  refreshSubscribers = []
}

apiClient.interceptors.response.use(
  (response: AxiosResponse) => response,
  async (error) => {
    const originalRequest = error.config

    if (error.response?.status === 401 && !originalRequest._retry) {
      if (isRefreshing) {
        // Queue this request until refresh completes
        return new Promise((resolve) => {
          refreshSubscribers.push((token: string) => {
            originalRequest.headers.Authorization = `Bearer ${token}`
            resolve(apiClient(originalRequest))
          })
        })
      }

      originalRequest._retry = true
      isRefreshing = true

      try {
        const refreshToken = useAuthStore.getState().refreshToken
        if (!refreshToken) throw new Error('No refresh token')

        const { data } = await axios.post(`${BASE_URL}/auth/refresh`, { refreshToken })

        const newAccessToken = data.accessToken
        useAuthStore.getState().setAccessToken(newAccessToken)
        onRefreshed(newAccessToken)

        originalRequest.headers.Authorization = `Bearer ${newAccessToken}`
        return apiClient(originalRequest)
      } catch (refreshError) {
        // Refresh failed - logout user
        useAuthStore.getState().logout()
        window.location.href = '/login'
        return Promise.reject(refreshError)
      } finally {
        isRefreshing = false
      }
    }

    return Promise.reject(error)
  }
)

// ============================================================
// Typed API response helper
// ============================================================
export const api = {
  get: <T>(url: string, params?: object) =>
    apiClient.get<T>(url, { params }).then((r) => r.data),

  post: <T>(url: string, data?: object) =>
    apiClient.post<T>(url, data).then((r) => r.data),

  put: <T>(url: string, data?: object) =>
    apiClient.put<T>(url, data).then((r) => r.data),

  patch: <T>(url: string, data?: object) =>
    apiClient.patch<T>(url, data).then((r) => r.data),

  delete: <T>(url: string) =>
    apiClient.delete<T>(url).then((r) => r.data),
}

// ============================================================
// Streaming SSE helper for AI responses
// ============================================================
export const streamAiResponse = async (
  endpoint: string,
  body: object,
  onChunk: (text: string) => void,
  onComplete: () => void,
  onError: (err: Error) => void
) => {
  const token = useAuthStore.getState().accessToken

  try {
    const response = await fetch(`${BASE_URL}${endpoint}`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${token}`,
        Accept: 'text/event-stream',
      },
      body: JSON.stringify(body),
    })

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`)
    }

    const reader = response.body?.getReader()
    if (!reader) throw new Error('No response body')

    const decoder = new TextDecoder()

    while (true) {
      const { done, value } = await reader.read()
      if (done) {
        onComplete()
        break
      }

      const chunk = decoder.decode(value, { stream: true })
      const lines = chunk.split('\n')

      for (const line of lines) {
        if (line.startsWith('data:')) {
          const text = line.slice(5).trim()
          if (text && text !== '[DONE]') {
            onChunk(text)
          }
        }
      }
    }
  } catch (err) {
    onError(err instanceof Error ? err : new Error('Streaming failed'))
  }
}

export default apiClient
