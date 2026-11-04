import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { SeoService } from '../../core/seo/seo.service';
import { DOCUMENT } from '@angular/common';

@Component({
  selector: 'gi-privacy-policy',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './privacy-policy.component.html',
  styleUrl: './privacy-policy.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PrivacyPolicyComponent {
  private readonly seo = inject(SeoService);
  private readonly document = inject(DOCUMENT);

  constructor() {
    this.seo.set({
      title: 'Privacy Policy — Gadaa Istar',
      description: 'Learn how Gadaa Istar protects your privacy when you purchase Ethiopian coffee and specialty exports.',
      canonicalUrl: `${this.document.location.origin}/privacy-policy`,
      ogType: 'article',
      jsonLd: {
        '@context': 'https://schema.org',
        '@type': 'WebPage',
        name: 'Privacy Policy',
        description: 'Privacy policy for Gadaa Istar',
      },
    });
  }
}
