import 'i18next'
import type { Translations } from './en'

// Makes t('...') check its keys against en.ts at compile time
declare module 'i18next' {
  interface CustomTypeOptions {
    defaultNS: 'translation'
    resources: { translation: Translations }
  }
}
