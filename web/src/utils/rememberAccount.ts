import type { LoginRole } from '@/stores/user'

const STORAGE_KEY = 'remembered-account'
const VALIDITY_MS = 7 * 24 * 60 * 60 * 1000

export interface RememberedAccount {
  username: string
  role: LoginRole
  expiresAt: number
  method?: 'username' | 'email'
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
      (account.method !== undefined &&
        account.method !== 'username' &&
        account.method !== 'email') ||
      typeof account.expiresAt !== 'number' ||
      !Number.isFinite(account.expiresAt) ||
      account.expiresAt <= Date.now()
    ) {
      clearRememberedAccount()
      return null
    }

    return {
      username: account.username,
      role: account.role,
      expiresAt: account.expiresAt,
      ...(account.method === 'email' ? { method: 'email' as const } : {}),
    }
  } catch {
    clearRememberedAccount()
    return null
  }
}

export function saveRememberedAccount(
  username: string,
  role: LoginRole,
  method: 'username' | 'email' = 'username',
): void {
  const account: RememberedAccount = {
    username,
    role,
    expiresAt: Date.now() + VALIDITY_MS,
    ...(method === 'email' ? { method } : {}),
  }
  try {
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(account))
  } catch (error) {
    console.warn('Failed to save remembered account:', error)
  }
}
