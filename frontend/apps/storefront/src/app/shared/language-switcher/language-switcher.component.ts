import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';

import { I18nService } from '../../core/i18n/i18n.service';
import { SupportedLocale } from '../../core/i18n/i18n.model';

@Component({
  selector: 'gi-language-switcher',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="gi-lang">
      @for (opt of i18n.localeOptions; track opt.code) {
        <button
          type="button"
          class="gi-lang__btn"
          [class.is-active]="i18n.locale() === opt.code"
          (click)="select(opt.code)"
          [attr.aria-label]="opt.label">
          <span class="gi-lang__flag">{{ opt.flag }}</span>
          <span class="gi-lang__code">{{ opt.code | uppercase }}</span>
        </button>
      }
    </div>
  `,
  styles: [`
    .gi-lang {
      display: inline-flex;
      gap: 0.25rem;
      align-items: center;

      &__btn {
        display: inline-flex;
        align-items: center;
        gap: 0.2rem;
        background: transparent;
        border: 1px solid transparent;
        border-radius: 6px;
        padding: 0.25rem 0.45rem;
        cursor: pointer;
        font-size: 0.75rem;
        color: rgba(255, 255, 255, 0.7);
        transition: all 0.15s;

        &:hover {
          background: rgba(255, 255, 255, 0.1);
          color: #fff;
        }

        &.is-active {
          background: rgba(255, 255, 255, 0.15);
          color: #fff;
          border-color: rgba(255, 255, 255, 0.2);
        }
      }

      &__flag {
        font-size: 0.9rem;
        line-height: 1;
      }

      &__code {
        font-weight: 600;
        letter-spacing: 0.03em;
      }
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LanguageSwitcherComponent {
  protected readonly i18n = inject(I18nService);

  select(code: SupportedLocale): void {
    this.i18n.setLocale(code);
  }
}
