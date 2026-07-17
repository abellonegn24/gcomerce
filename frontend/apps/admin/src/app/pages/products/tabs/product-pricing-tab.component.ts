import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';

import { ProductPricingComponent } from '../product-pricing.component';
import { ProductEditorStateService } from '../product-editor-state.service';

/**
 * Routed at {@code /products/:id/pricing}. Waits for the shell to resolve
 * the product id, then forwards it to the existing pricing panel.
 */
@Component({
  selector: 'gi-admin-product-pricing-tab',
  standalone: true,
  imports: [CommonModule, ProductPricingComponent],
  template: `
    @if (state.product(); as p) {
      <gi-admin-product-pricing [productId]="p.id" />
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProductPricingTabComponent {
  protected readonly state = inject(ProductEditorStateService);
}
