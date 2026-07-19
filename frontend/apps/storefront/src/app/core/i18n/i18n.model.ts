export type SupportedLocale = 'en' | 'am' | 'om';

export interface LocaleOption {
  code: SupportedLocale;
  label: string;
  flag: string;
}

export const LOCALE_OPTIONS: LocaleOption[] = [
  { code: 'en', label: 'English',    flag: '🇬🇧' },
  { code: 'am', label: 'አማርኛ',       flag: '🇪🇹' },
  { code: 'om', label: 'Afaan Oromoo', flag: '🇪🇹' },
];

export type TranslationKey =
  | 'nav.rawCoffee'
  | 'nav.roastedCoffee'
  | 'nav.whiteProducts'
  | 'nav.spicyProducts'
  | 'nav.oilSeed'
  | 'nav.livestock'
  | 'nav.mining'
  | 'nav.wholesale'
  | 'nav.about'
  | 'nav.contact'
  | 'nav.trackOrder'
  | 'action.signIn'
  | 'action.signUp'
  | 'action.signOut'
  | 'action.search'
  | 'action.addToCart'
  | 'action.viewCart'
  | 'action.checkout'
  | 'action.viewDetails'
  | 'label.currency'
  | 'label.language'
  | 'label.cart'
  | 'label.wishlist'
  | 'label.account'
  | 'hero.title'
  | 'hero.subtitle'
  | 'hero.ctaShop'
  | 'hero.ctaWholesale'
  | 'footer.rights'
  | 'footer.motto';

export type TranslationMap = Partial<Record<TranslationKey, string>>;
