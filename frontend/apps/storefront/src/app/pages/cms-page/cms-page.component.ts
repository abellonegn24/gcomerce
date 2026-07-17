import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  inject,
  signal,
} from '@angular/core';
import { CommonModule, DOCUMENT } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';

import { StorefrontCmsService } from '../../core/cms/cms.service';
import { CmsPageDto } from '../../core/cms/cms.model';
import { SeoService } from '../../core/seo/seo.service';

@Component({
  selector: 'gi-cms-page',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './cms-page.component.html',
  styleUrl: './cms-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CmsPageComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly cms = inject(StorefrontCmsService);
  private readonly sanitizer = inject(DomSanitizer);
  private readonly seo = inject(SeoService);
  private readonly document = inject(DOCUMENT);

  protected readonly page = signal<CmsPageDto | null>(null);
  protected readonly bodyHtml = signal<SafeHtml | null>(null);
  protected readonly loading = signal<boolean>(true);
  protected readonly error = signal<string | null>(null);

  ngOnInit(): void {
    const slug = this.route.snapshot.paramMap.get('slug');
    if (!slug) {
      this.router.navigateByUrl('/');
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    this.cms.getPage(slug).subscribe({
      next: (dto) => {
        this.page.set(dto);
        this.bodyHtml.set(
          dto.bodyHtml
            ? this.sanitizer.bypassSecurityTrustHtml(dto.bodyHtml)
            : null,
        );
        this.loading.set(false);
        this.seo.set({
          title: dto.seoTitle || dto.title,
          description: dto.seoDescription || undefined,
          canonicalUrl: `${this.document.location.origin}/pages/${slug}`,
          ogType: 'article',
          jsonLd: {
            '@context': 'https://schema.org',
            '@type': 'WebPage',
            name: dto.title,
            description: dto.seoDescription || undefined,
          },
        });
      },
      error: () => {
        this.error.set('Page not found.');
        this.loading.set(false);
      },
    });
  }
}
