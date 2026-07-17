import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';

import { ProductInventoryComponent } from '../product-inventory.component';
import { ProductEditorStateService } from '../product-editor-state.service';

/**
 * Routed at {@code /products/:id/inventory}. Waits for the shell to resolve
 * the product id, then forwards it to the existing inventory panel.
 */
@Component({
  selector: 'gi-admin-product-inventory-tab',
  standalone: true,
  imports: [CommonModule, ProductInventoryComponent],
  template: `
    @if (state.product(); as p) {
      <gi-admin-product-inventory [productId]="p.id" />
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProductInventoryTabComponent {
  protected readonly state = inject(ProductEditorStateService);
}
