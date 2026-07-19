import { ChangeDetectionStrategy, Component, OnInit, OnDestroy, afterNextRender, inject, signal } from '@angular/core';
import { CommonModule, DOCUMENT } from '@angular/common';
import { RouterLink } from '@angular/router';

import { StorefrontCmsService } from '../../core/cms/cms.service';
import { CmsBannerDto } from '../../core/cms/cms.model';
import { CatalogService } from '../../core/catalog/catalog.service';
import { ProductCard } from '../../core/catalog/catalog.model';
import { SeoService } from '../../core/seo/seo.service';
import { NormalizedApiError } from '../../core/http/error.interceptor';

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
export class HomeComponent implements OnInit, OnDestroy {
  private readonly cms = inject(StorefrontCmsService);
  private readonly catalog = inject(CatalogService);
  private readonly seo = inject(SeoService);
  private readonly document = inject(DOCUMENT);

  protected readonly banners = signal<CmsBannerDto[]>([]);
  protected readonly bannersLoading = signal<boolean>(true);

  protected readonly products = signal<ProductCard[]>([]);
  protected readonly productsLoading = signal<boolean>(true);
  protected readonly slideIndex = signal<number>(0);
  private slideTimer: ReturnType<typeof setInterval> | null = null;

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
  }

  constructor() {
    // Fetch data only in the browser — afterNextRender never runs during SSR,
    // so the server render completes instantly without waiting on the API.
    afterNextRender(() => {
      this.cms.listBanners('HOMEPAGE_HERO').subscribe({
        next: (res) => {
          this.banners.set(res.banners ?? []);
          this.bannersLoading.set(false);
        },
        error: () => {
          this.bannersLoading.set(false);
        },
      });

      this.catalog.list({ page: 0, size: 8 }).subscribe({
        next: (res) => {
          this.products.set(res.page.data);
          this.productsLoading.set(false);
          if (res.page.data.length > 1) {
            this.startAutoSlide(res.page.data.length);
          }
        },
        error: (_err: NormalizedApiError) => {
          this.productsLoading.set(false);
        },
      });
    });
  }

  ngOnDestroy(): void {
    this.stopAutoSlide();
  }

  protected goToSlide(i: number): void {
    const max = this.products().length;
    if (max === 0) return;
    this.slideIndex.set(((i % max) + max) % max);
    this.restartAutoSlide();
  }

  protected nextSlide(): void {
    this.goToSlide(this.slideIndex() + 1);
  }

  protected prevSlide(): void {
    this.goToSlide(this.slideIndex() - 1);
  }

  protected formatPrice(currency: string, amount: string): string {
    const value = Number(amount);
    if (!isFinite(value)) return `${currency} ${amount}`;
    try {
      return new Intl.NumberFormat('en-US', {
        style: 'currency',
        currency,
        minimumFractionDigits: 2,
        maximumFractionDigits: 2,
      }).format(value);
    } catch {
      return `${currency} ${value.toFixed(2)}`;
    }
  }

  private startAutoSlide(count: number): void {
    if (count <= 1) return;
    this.slideTimer = setInterval(() => {
      this.slideIndex.update((i) => (i + 1) % count);
    }, 5000);
  }

  private stopAutoSlide(): void {
    if (this.slideTimer) {
      clearInterval(this.slideTimer);
      this.slideTimer = null;
    }
  }

  private restartAutoSlide(): void {
    this.stopAutoSlide();
    const count = this.products().length;
    if (count > 1) this.startAutoSlide(count);
  }
}
