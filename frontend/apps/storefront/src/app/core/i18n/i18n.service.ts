import { Injectable, computed, signal } from '@angular/core';

import {
  SupportedLocale,
  TranslationKey,
  TranslationMap,
  LOCALE_OPTIONS,
} from './i18n.model';
import { enTranslations } from './translations/en';
import { amTranslations } from './translations/am';
import { omTranslations } from './translations/om';

const STORAGE_KEY = 'gi-locale';
const CURRENCY_KEY = 'gi-currency';

const translations: Record<SupportedLocale, TranslationMap> = {
  en: enTranslations,
  am: amTranslations,
  om: omTranslations,
};

const localeToCurrency: Record<SupportedLocale, 'USD' | 'ETB'> = {
  en: 'USD',
  am: 'ETB',
  om: 'ETB',
};

/**
 * Lightweight runtime i18n service — no @angular/localize compile-time
 * extraction needed. Uses a signal-based reactive locale so any component
 * can call `t('nav.about')` in templates and re-render on locale change.
 *
 * Persists locale + currency to localStorage. Defaults to 'en'.
 */
@Injectable({ providedIn: 'root' })
export class I18nService {
  private readonly _locale = signal<SupportedLocale>(this.readStoredLocale());
  private readonly _currency = signal<'USD' | 'ETB'>(this.readStoredCurrency());

  readonly locale = this._locale.asReadonly();
  readonly currency = this._currency.asReadonly();
  readonly localeOptions = LOCALE_OPTIONS;

  /** Reactive translation function for use in templates: `t('nav.about')()`. */
  t = computed(() => {
    const locale = this._locale();
    const map = translations[locale] ?? translations.en;
    return (key: TranslationKey): string => map[key] ?? translations.en[key] ?? key;
  });

  setLocale(locale: SupportedLocale): void {
    this._locale.set(locale);
    if (typeof localStorage !== 'undefined') {
      localStorage.setItem(STORAGE_KEY, locale);
    }
  }

  setCurrency(code: 'USD' | 'ETB'): void {
    this._currency.set(code);
    if (typeof localStorage !== 'undefined') {
      localStorage.setItem(CURRENCY_KEY, code);
    }
  }

  /** Format a price string (numeric) with the current currency. */
  formatPrice(amount: string | number): string {
    const value = typeof amount === 'string' ? parseFloat(amount) : amount;
    if (isNaN(value)) return amount as string;
    const currency = this._currency();
    try {
      return new Intl.NumberFormat(this.locale(), {
        style: 'currency',
        currency,
        minimumFractionDigits: 2,
      }).format(value);
    } catch {
      return `${currency} ${value.toFixed(2)}`;
    }
  }

  private readStoredLocale(): SupportedLocale {
    if (typeof localStorage === 'undefined') return 'en';
    const stored = localStorage.getItem(STORAGE_KEY) as SupportedLocale | null;
    if (stored && stored in translations) return stored;
    return 'en';
  }

  private readStoredCurrency(): 'USD' | 'ETB' {
    if (typeof localStorage === 'undefined') return localeToCurrency[this._locale()];
    const stored = localStorage.getItem(CURRENCY_KEY) as 'USD' | 'ETB' | null;
    if (stored && ['USD', 'ETB'].includes(stored)) return stored;
    return localeToCurrency[this._locale()];
  }
}
