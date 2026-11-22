import axios from 'axios'

const TOKEN_KEY = 'routing.token'

/**
 * Axios instance: adds the JWT to every request and logs out on 401.
 * In development Vite proxies /api to the backend; in production VITE_API_URL
 * points at the backend's public URL.
 */
const API_URL = (import.meta.env.VITE_API_URL || '').replace(/\/$/, '')
const api = axios.create({ baseURL: `${API_URL}/api` })

api.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY)
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401 && !error.config.url.includes('/auth/login')) {
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem('routing.user')
      window.location.href = '/login'
    }
    return Promise.reject(error)
  },
)

/** Turns an API error into a readable message (includes validation errors). */
export function errorMessage(error) {
  const data = error?.response?.data
  if (data?.errors?.length) {
    return `${data.message}: ${data.errors.join('; ')}`
  }
  return data?.message || error?.message || 'Something went wrong'
}

export { TOKEN_KEY }
export default api
