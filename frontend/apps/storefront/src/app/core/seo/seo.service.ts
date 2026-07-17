import { Injectable, inject } from '@angular/core';
import { DOCUMENT } from '@angular/common';

export interface SeoData {
  title: string;
  description?: string;
  canonicalUrl?: string;
  jsonLd?: Record<string, unknown>;
  ogType?: string;
  ogImage?: string;
}

/**
 * Lightweight SEO service — manages <title>, <meta> description,
 * canonical <link>, Open Graph tags, and JSON-LD <script type="application/ld+json">.
 *
 * Uses the DOM API directly to avoid heavy dependencies. Called from
 * route components' ngOnInit to set per-page SEO metadata.
 */
@Injectable({ providedIn: 'root' })
export class SeoService {
  private readonly document = inject(DOCUMENT);
  private jsonLdId = 'gi-jsonld-dynamic';

  set(data: SeoData): void {
    this.setTitle(data.title);
    if (data.description) this.setMeta('description', data.description);
    if (data.canonicalUrl) this.setCanonical(data.canonicalUrl);
    if (data.ogType) this.setMetaProperty('og:type', data.ogType);
    if (data.ogImage) this.setMetaProperty('og:image', data.ogImage);
    this.setMetaProperty('og:title', data.title);
    if (data.description) this.setMetaProperty('og:description', data.description);
    if (data.jsonLd) this.setJsonLd(data.jsonLd);
  }

  setTitle(title: string): void {
    this.document.title = title;
  }

  setMeta(name: string, content: string): void {
    let el = this.document.querySelector(`meta[name="${name}"]`);
    if (!el) {
      el = this.document.createElement('meta');
      el.setAttribute('name', name);
      this.document.head.appendChild(el);
    }
    el.setAttribute('content', content);
  }

  setMetaProperty(property: string, content: string): void {
    let el = this.document.querySelector(`meta[property="${property}"]`);
    if (!el) {
      el = this.document.createElement('meta');
      el.setAttribute('property', property);
      this.document.head.appendChild(el);
    }
    el.setAttribute('content', content);
  }

  setCanonical(href: string): void {
    let el = this.document.querySelector('link[rel="canonical"]');
    if (!el) {
      el = this.document.createElement('link');
      el.setAttribute('rel', 'canonical');
      this.document.head.appendChild(el);
    }
    el.setAttribute('href', href);
  }

  setJsonLd(data: Record<string, unknown>): void {
    let el = this.document.getElementById(this.jsonLdId) as HTMLScriptElement | null;
    if (!el) {
      el = this.document.createElement('script');
      el.id = this.jsonLdId;
      el.setAttribute('type', 'application/ld+json');
      this.document.head.appendChild(el);
    }
    el.textContent = JSON.stringify(data);
  }

  clearJsonLd(): void {
    const el = this.document.getElementById(this.jsonLdId);
    if (el) el.remove();
  }
}
