import { createContext, useContext, useState } from 'react'
import api, { TOKEN_KEY } from '../api/client.js'

const USER_KEY = 'routing.user'
const AuthContext = createContext(null)

function readUser() {
  try {
    return JSON.parse(localStorage.getItem(USER_KEY))
  } catch {
    return null
  }
}

export function AuthProvider({ children }) {
  const [user, setUser] = useState(readUser)

  const login = async (username, password) => {
    const { data } = await api.post('/auth/login', { username, password })
    localStorage.setItem(TOKEN_KEY, data.token)
    const loggedIn = { username: data.username, role: data.role }
    localStorage.setItem(USER_KEY, JSON.stringify(loggedIn))
    setUser(loggedIn)
  }

  const logout = () => {
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(USER_KEY)
    setUser(null)
  }

  const isAdmin = user?.role === 'ADMIN'

  return <AuthContext.Provider value={{ user, isAdmin, login, logout }}>{children}</AuthContext.Provider>
}

// eslint-disable-next-line react-refresh/only-export-components
export function useAuth() {
  return useContext(AuthContext)
}
