import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';

import { ProductVariantsComponent } from '../product-variants.component';
import { ProductEditorStateService } from '../product-editor-state.service';

/**
 * Routed at {@code /products/:id/variants}. Waits for the shell to resolve
 * the product id, then forwards it to the variants panel.
 */
@Component({
  selector: 'gi-admin-product-variants-tab',
  standalone: true,
  imports: [CommonModule, ProductVariantsComponent],
  template: `
    @if (state.product(); as p) {
      <gi-admin-product-variants [productId]="p.id" />
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProductVariantsTabComponent {
  protected readonly state = inject(ProductEditorStateService);
}
