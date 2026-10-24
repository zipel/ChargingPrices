import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'
import { setNumberLocale } from '../lib/price'
import { da } from './da'
import { en } from './en'

export type Language = 'en' | 'da'

const STORAGE_KEY = 'chargingprices.language'

/** The last choice, otherwise Danish for browsers set to Danish, otherwise English. */
function initialLanguage(): Language {
  try {
    const saved = window.localStorage.getItem(STORAGE_KEY)
    if (saved === 'en' || saved === 'da') return saved
  } catch {
    // Storage can be blocked (private mode); fall back to the browser language
  }
  return navigator.language.toLowerCase().startsWith('da') ? 'da' : 'en'
}

/** Locale for numbers and times: Danish writes 3,46 and English 3.46. */
export function localeOf(language: string): string {
  return language === 'da' ? 'da-DK' : 'en-GB'
}

export function changeLanguage(language: Language) {
  try {
    window.localStorage.setItem(STORAGE_KEY, language)
  } catch {
    // Not saved; the choice still applies until the page is closed
  }
  void i18n.changeLanguage(language)
}

i18n.on('languageChanged', (language) => {
  setNumberLocale(localeOf(language))
  document.documentElement.lang = language
})

void i18n.use(initReactI18next).init({
  resources: { en: { translation: en }, da: { translation: da } },
  lng: initialLanguage(),
  fallbackLng: 'en',
  // React already escapes output
  interpolation: { escapeValue: false },
})

export default i18n
