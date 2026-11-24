import { formatNumber, getNumberOfCurrencyDigits } from '@angular/common';
import { LOCALE_ID, Pipe, PipeTransform, inject } from '@angular/core';

/**
 * Decimals for a currency, from Angular's built-in currency data (VND = 0, MYR/SGD/... = 2).
 * Angular follows ISO 4217 for IDR (2 decimals), but rupiah amounts are in practice shown without
 * decimals, so IDR is overridden to 0.
 */
export function currencyDigits(currency: string): number {
  return currency === 'IDR' ? 0 : getNumberOfCurrencyDigits(currency);
}

/**
 * `{{ 1234.5 | money: 'MYR' }}` → "MYR 1,234.50". Shows the ISO code rather than a symbol ("$" is ambiguous
 * across SGD and others). Angular's own `currency` pipe would print "MYR1,234.50" with no space.
 */
@Pipe({ name: 'money' })
export class MoneyPipe implements PipeTransform {
  private readonly locale = inject(LOCALE_ID);

  transform(amount: number | null | undefined, currency: string | undefined): string {
    if (amount == null || !currency) return '';
    const d = currencyDigits(currency);
    return `${currency} ${formatNumber(amount, this.locale, `1.${d}-${d}`)}`;
  }
}
