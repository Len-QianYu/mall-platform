import type { LoginRole } from '@/stores/user'

const STORAGE_KEY = 'remembered-account'
const VALIDITY_MS = 7 * 24 * 60 * 60 * 1000

export interface RememberedAccount {
  username: string
  role: LoginRole
  expiresAt: number
}

export function clearRememberedAccount(): void {
  try {
    window.localStorage.removeItem(STORAGE_KEY)
  } catch (error) {
    console.warn('Failed to clear remembered account:', error)
  }
}

export function loadRememberedAccount(): RememberedAccount | null {
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY)
    if (!raw) return null

    const account = JSON.parse(raw) as Partial<RememberedAccount> | null
    if (
      !account ||
      typeof account.username !== 'string' ||
      !account.username.trim() ||
      (account.role !== 'user' && account.role !== 'admin') ||
      typeof account.expiresAt !== 'number' ||
      !Number.isFinite(account.expiresAt) ||
      account.expiresAt <= Date.now()
    ) {
      clearRememberedAccount()
      return null
    }

    return { username: account.username, role: account.role, expiresAt: account.expiresAt }
  } catch {
    clearRememberedAccount()
    return null
  }
}

export function saveRememberedAccount(username: string, role: LoginRole): void {
  const account: RememberedAccount = { username, role, expiresAt: Date.now() + VALIDITY_MS }
  try {
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(account))
  } catch (error) {
    console.warn('Failed to save remembered account:', error)
  }
}
