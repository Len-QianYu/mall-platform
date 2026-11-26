import { isAxiosError } from 'axios'
import type { ApiResult } from '@/types/api'

export function apiErrorMessage(error: unknown, fallback: string): string {
  return isAxiosError<ApiResult>(error)
    ? error.response?.data?.payload?.message || fallback
    : fallback
}

export function apiRetryAfter(error: unknown): number {
  if (!isAxiosError<ApiResult>(error) || error.response?.data?.status !== false) return 0
  const seconds = error.response.data.payload.retryAfterSeconds
  return typeof seconds === 'number' && Number.isFinite(seconds) && seconds > 0 ? seconds : 0
}
