import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';

import { ProductMediaComponent } from '../product-media.component';
import { ProductEditorStateService } from '../product-editor-state.service';
import { ProductMedia } from '../../../core/catalog/catalog.model';

/**
 * Routed at {@code /products/:id/media}. Wraps the existing media manager
 * and pipes its post-mutation media list back into the shell state so the
 * primary-image / count derivations elsewhere stay fresh.
 */
@Component({
  selector: 'gi-admin-product-media-tab',
  standalone: true,
  imports: [CommonModule, ProductMediaComponent],
  template: `
    @if (state.product(); as p) {
      <gi-admin-product-media
        [productId]="p.id"
        [media]="p.media"
        (changed)="onMediaChanged($event)" />
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProductMediaTabComponent {
  protected readonly state = inject(ProductEditorStateService);

  protected onMediaChanged(list: ProductMedia[]): void {
    this.state.patchMedia(list);
  }
}
