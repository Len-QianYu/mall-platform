import { computed, readonly, ref } from 'vue'
import en from 'element-plus/es/locale/lang/en'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import { english } from './en'
import { englishErrors } from './errors'

export type Locale = 'zh-CN' | 'en'
export type MessageParams = Record<string, string | number | null | undefined>
export const LOCALE_STORAGE_KEY = 'prompt-pals-locale'

const readLocale = (): Locale => {
  try {
    return localStorage.getItem(LOCALE_STORAGE_KEY) === 'en' ? 'en' : 'zh-CN'
  } catch {
    return 'zh-CN'
  }
}

const currentLocale = ref<Locale>(readLocale())
const locale = readonly(currentLocale)
const elementLocale = computed(() => (locale.value === 'en' ? en : zhCn))

// Chinese source messages also serve as stable keys; account and product data stay untouched.
export function t(message: string | null | undefined, params: MessageParams = {}): string {
  const source = message ?? ''
  const key = source.trim().replace(/\s+/g, ' ')
  const translation = Object.prototype.hasOwnProperty.call(english, key)
    ? english[key]
    : Object.prototype.hasOwnProperty.call(englishErrors, key)
      ? englishErrors[key]
      : undefined
  const translated = locale.value === 'en' ? (translation ?? source) : source
  return translated.replace(/\{(\w+)\}/g, (token, key: string) =>
    !Object.prototype.hasOwnProperty.call(params, key) ||
    params[key] === null ||
    params[key] === undefined
      ? token
      : String(params[key]),
  )
}

export function setLocale(next: Locale): void {
  currentLocale.value = next
  try {
    localStorage.setItem(LOCALE_STORAGE_KEY, next)
  } catch {
    // Language switching remains available when browser storage is disabled.
  }
}

export function formatNumber(value: number, options?: Intl.NumberFormatOptions): string {
  return value.toLocaleString(locale.value === 'en' ? 'en-US' : 'zh-CN', options)
}

export function formLabelWidth(value: string): string {
  const width = Number.parseFloat(value)
  return locale.value === 'en' && Number.isFinite(width) ? `${Math.max(128, width)}px` : value
}

export function useLocale() {
  const toggleLocale = () => setLocale(locale.value === 'zh-CN' ? 'en' : 'zh-CN')
  return { locale, elementLocale, setLocale, toggleLocale, t }
}
