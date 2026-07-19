import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';

import { ProductFormComponent } from '../product-form.component';
import { ProductEditorStateService } from '../product-editor-state.service';
import {
  CreateProductPayload,
  UpdateProductPayload,
  UploadResponse,
} from '../../../core/catalog/catalog.model';

/**
 * Routed at {@code /products/:id/details}. Thin adapter that plugs the shell
 * state service into the existing {@link ProductFormComponent}. Keeps the
 * form component reusable by both {@code /products/new} and the edit shell.
 */
@Component({
  selector: 'gi-admin-product-details-tab',
  standalone: true,
  imports: [CommonModule, ProductFormComponent],
  template: `
    @if (state.product(); as p) {
      <gi-admin-product-form
        [mode]="'edit'"
        [initial]="p"
        [submitting]="state.saving()"
        [errorMessage]="state.errorMessage()"
        [successMessage]="state.successMessage()"
        (save)="onSave($event)"
        (imageChange)="onImageChange($event)"
        (cancel)="onCancel()" />
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProductDetailsTabComponent {
  protected readonly state = inject(ProductEditorStateService);
  private readonly router = inject(Router);

  protected onSave(payload: CreateProductPayload | UpdateProductPayload): void {
    // In edit mode the payload is always an UpdateProductPayload; the union
    // exists so ProductFormComponent stays reusable by /products/new.
    this.state.saveDetails(payload as UpdateProductPayload);
  }

  protected onImageChange(img: UploadResponse | null): void {
    if (img) {
      this.state.attachImage(img, () => {});
    }
  }

  protected onCancel(): void {
    void this.router.navigateByUrl('/products');
  }
}
