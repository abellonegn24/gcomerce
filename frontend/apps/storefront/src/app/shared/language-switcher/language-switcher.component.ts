import { ChangeDetectionStrategy, Component, HostListener, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';

import { I18nService } from '../../core/i18n/i18n.service';
import { SupportedLocale } from '../../core/i18n/i18n.model';

@Component({
  selector: 'gi-language-switcher',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="gi-lang" (click)="toggle($event)" (keydown.enter)="toggle($event)" tabindex="0" role="button" aria-haspopup="listbox" [attr.aria-expanded]="open()">
      <span class="gi-lang__flag">{{ current().flag }}</span>
      <span class="gi-lang__code">{{ current().code | uppercase }}</span>
      <span class="material-icons-round gi-lang__chevron" [class.is-open]="open()">expand_more</span>

      @if (open()) {
        <ul class="gi-lang__menu" role="listbox" (click)="$event.stopPropagation()">
          @for (opt of i18n.localeOptions; track opt.code) {
            <li
              role="option"
              [attr.aria-selected]="i18n.locale() === opt.code"
              class="gi-lang__option"
              [class.is-active]="i18n.locale() === opt.code"
              (click)="select($event, opt.code)"
              tabindex="0"
              (keydown.enter)="select($event, opt.code)">
              <span class="gi-lang__flag">{{ opt.flag }}</span>
              <span class="gi-lang__label">{{ opt.label }}</span>
              @if (i18n.locale() === opt.code) {
                <span class="material-icons-round gi-lang__check">check</span>
              }
            </li>
          }
        </ul>
      }
    </div>
  `,
  styles: [`
    .gi-lang {
      position: relative;
      display: inline-flex;
      align-items: center;
      gap: 0.3rem;
      background: transparent;
      border: 1px solid rgba(255, 255, 255, 0.2);
      border-radius: 6px;
      padding: 0.28rem 0.5rem;
      cursor: pointer;
      font-size: 0.75rem;
      color: rgba(255, 255, 255, 0.85);
      transition: all 0.15s;
      white-space: nowrap;
      outline: none;

      &:hover {
        background: rgba(255, 255, 255, 0.1);
        color: #fff;
        border-color: rgba(255, 255, 255, 0.35);
      }

      &:focus-visible {
        border-color: var(--gi-red-500);
      }
    }

    .gi-lang__flag {
      font-size: 0.9rem;
      line-height: 1;
    }

    .gi-lang__code {
      font-weight: 600;
      letter-spacing: 0.03em;
    }

    .gi-lang__chevron {
      font-size: 1rem;
      transition: transform 0.2s;

      &.is-open {
        transform: rotate(180deg);
      }
    }

    .gi-lang__menu {
      position: absolute;
      top: calc(100% + 6px);
      right: 0;
      list-style: none;
      margin: 0;
      padding: 0.35rem;
      background: #fff;
      border-radius: 8px;
      box-shadow: 0 8px 24px rgba(0, 0, 0, 0.18);
      min-width: 160px;
      z-index: 200;
    }

    .gi-lang__option {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      padding: 0.5rem 0.6rem;
      border-radius: 6px;
      cursor: pointer;
      font-size: 0.85rem;
      color: var(--gi-navy-900, #0A0A0A);
      transition: background 0.12s;
      white-space: nowrap;
      outline: none;

      &:hover {
        background: rgba(214, 58, 47, 0.08);
      }

      &:focus-visible {
        background: rgba(214, 58, 47, 0.12);
      }

      &.is-active {
        color: var(--gi-red-600, #C93327);
        font-weight: 600;
      }
    }

    .gi-lang__label {
      flex: 1;
    }

    .gi-lang__check {
      font-size: 1rem;
      color: var(--gi-red-600, #C93327);
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LanguageSwitcherComponent {
  protected readonly i18n = inject(I18nService);
  protected readonly open = signal(false);

  protected readonly current = computed(() =>
    this.i18n.localeOptions.find((o) => o.code === this.i18n.locale()) ?? this.i18n.localeOptions[0]
  );

  @HostListener('document:click')
  onDocumentClick(): void {
    this.open.set(false);
  }

  toggle(event?: Event): void {
    event?.stopPropagation();
    this.open.update((v) => !v);
  }

  select(event: Event, code: SupportedLocale): void {
    event.stopPropagation();
    this.i18n.setLocale(code);
    this.open.set(false);
  }
}
