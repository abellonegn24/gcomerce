import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { SeoService } from '../../core/seo/seo.service';
import { DOCUMENT } from '@angular/common';

@Component({
  selector: 'gi-terms-of-service',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './terms-of-service.component.html',
  styleUrl: './terms-of-service.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TermsOfServiceComponent {
  private readonly seo = inject(SeoService);
  private readonly document = inject(DOCUMENT);

  constructor() {
    this.seo.set({
      title: 'Terms of Service — Gadaa Istar',
      description: 'Read our terms of service for purchasing Ethiopian coffee and specialty exports from Gadaa Istar.',
      canonicalUrl: `${this.document.location.origin}/terms-of-service`,
      ogType: 'article',
      jsonLd: {
        '@context': 'https://schema.org',
        '@type': 'WebPage',
        name: 'Terms of Service',
        description: 'Terms and conditions for purchasing from Gadaa Istar',
      },
    });
  }
}
