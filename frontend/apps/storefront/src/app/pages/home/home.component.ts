import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule, DOCUMENT } from '@angular/common';
import { RouterLink } from '@angular/router';

import { StorefrontCmsService } from '../../core/cms/cms.service';
import { CmsBannerDto } from '../../core/cms/cms.model';
import { SeoService } from '../../core/seo/seo.service';

interface CategoryTile {
  slug: string;
  label: string;
  description: string;
  icon: string;
}

@Component({
  selector: 'gi-home',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './home.component.html',
  styleUrl: './home.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HomeComponent implements OnInit {
  private readonly cms = inject(StorefrontCmsService);
  private readonly seo = inject(SeoService);
  private readonly document = inject(DOCUMENT);

  protected readonly banners = signal<CmsBannerDto[]>([]);
  protected readonly bannersLoading = signal<boolean>(true);

  protected readonly tiles: CategoryTile[] = [
    {
      slug: 'raw-coffee',
      label: 'Raw Green Coffee',
      description: 'Sidamo, Yirgacheffe, Harrar, Guji — export-grade beans in 60 kg jute bags.',
      icon: 'grain',
    },
    {
      slug: 'roasted-coffee',
      label: 'Roasted Coffee',
      description: 'Single-origin roasts and blends, ground or whole bean, 250 g – 5 kg.',
      icon: 'coffee',
    },
    {
      slug: 'oil-seed',
      label: 'Oil Seed',
      description: 'Sesame, Niger seed, and sunflower — export-grade oil seeds from Ethiopian farms.',
      icon: 'water_drop',
    },
    {
      slug: 'livestock',
      label: 'Livestock',
      description: 'Cattle, sheep, goats, and poultry from the Ethiopian highlands.',
      icon: 'agriculture',
    },
    {
      slug: 'mining',
      label: 'Mining',
      description: 'Gold, gemstones, and industrial minerals from Ethiopian mining operations.',
      icon: 'diamond',
    },
    {
      slug: 'white-products',
      label: 'White Products',
      description: 'Teff, injera flour, honey, and other white-agri exports.',
      icon: 'rice_bowl',
    },
    {
      slug: 'spicy-products',
      label: 'Spicy Products',
      description: 'Berbere, mitmita, korerima, and other Ethiopian spice blends.',
      icon: 'local_fire_department',
    },
  ];

  ngOnInit(): void {
    this.seo.set({
      title: 'Gadaa Istar — Ethiopian Coffee & Specialty Exports',
      description: 'Direct-from-origin Ethiopian coffee, honey, and spice exports from Gadaa Istar Business Group S.C.',
      canonicalUrl: this.document.location.origin,
      ogType: 'website',
      jsonLd: {
        '@context': 'https://schema.org',
        '@type': 'WebSite',
        name: 'Gadaa Istar',
        url: this.document.location.origin,
        potentialAction: {
          '@type': 'SearchAction',
          target: `${this.document.location.origin}/shop?q={search_term_string}`,
          'query-input': 'required name=search_term_string',
        },
      },
    });
    this.cms.listBanners('HOMEPAGE_HERO').subscribe({
      next: (res) => {
        this.banners.set(res.banners ?? []);
        this.bannersLoading.set(false);
      },
      error: () => {
        this.bannersLoading.set(false);
      },
    });
  }
}
