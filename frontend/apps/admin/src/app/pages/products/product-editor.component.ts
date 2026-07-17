import { ChangeDetectionStrategy, Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

import { ProductEditorStateService } from './product-editor-state.service';

type EditorTab = 'details' | 'media' | 'inventory' | 'pricing' | 'variants';

/**
 * /products/:id — product editor shell.
 *
 * <p>Owns the breadcrumb, header, tab bar, banners and archive button.
 * The actual tab panels are rendered through {@code <router-outlet>} into
 * child routes ({@code /products/:id/details} etc.) so every tab is a
 * real URL that can be deep-linked, back-buttoned, and shared. Historically
 * the tabs were a local {@code signal<'details' | 'media' | ...>()} which
 * silently broke every out-of-app link to a specific tab (they all landed
 * on Details).
 *
 * <p>Shared state (the loaded product, save/error/success flags, media
 * patching) lives in {@link ProductEditorStateService}, provided
 * component-locally so switching to a different product tears down the
 * cache and refetches. Children (see the four *-tab.component.ts files)
 * inject the same service to drive their UI.
 */
@Component({
  selector: 'gi-admin-product-editor',
  standalone: true,
  imports: [CommonModule, RouterLink, RouterLinkActive, RouterOutlet],
  templateUrl: './product-editor.component.html',
  styleUrl: './product-editor.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [ProductEditorStateService],
})
export class ProductEditorComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  protected readonly state = inject(ProductEditorStateService);

  protected readonly tabs: Array<{ id: EditorTab; label: string; icon: string }> = [
    { id: 'details',   label: 'Details',   icon: 'edit_note' },
    { id: 'media',     label: 'Media',     icon: 'perm_media' },
    { id: 'inventory', label: 'Inventory', icon: 'inventory_2' },
    { id: 'pricing',   label: 'Pricing',   icon: 'sell' },
    { id: 'variants',  label: 'Variants',  icon: 'category' },
  ];

  ngOnInit(): void {
    const rawId = this.route.snapshot.paramMap.get('id');
    const id = rawId ? Number(rawId) : NaN;
    if (!isFinite(id) || id <= 0) {
      // The state service already renders "Could not load" on a bad id, but
      // we skip the API call for an outright bogus one.
      return;
    }
    this.state.load(id);
  }

  // ---------------------------------------------------------------------------

  /** Called by the tab bar so banners don't linger across tab changes. */
  protected clearBanners(): void {
    this.state.clearBanners();
  }

  protected archive(): void {
    const p = this.state.product();
    if (!p) return;
    if (!confirm(`Archive product "${p.name}"?\n\nIt will disappear from the storefront (status → ARCHIVED). Historical orders keep working.`)) {
      return;
    }
    this.state.archive((ok) => {
      if (ok) {
        void this.router.navigate(['/products'], { queryParams: { archived: p.id } });
      }
    });
  }
}
