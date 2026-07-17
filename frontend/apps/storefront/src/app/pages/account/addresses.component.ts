import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { AddressService } from '../../core/addresses/address.service';
import {
  Address,
  AddressType,
  CreateAddressPayload,
  UpdateAddressPayload,
} from '../../core/addresses/address.model';
import { NormalizedApiError } from '../../core/http/error.interceptor';

type Mode = 'list' | 'create' | 'edit';

/**
 * /account/addresses — customer-facing address book.
 *
 * Supports full CRUD:
 *   - list saved addresses (cards)
 *   - create a new address (inline form)
 *   - edit an existing address (inline form pre-filled)
 *   - promote-to-default (PATCH isDefault=true)
 *   - delete (with confirm)
 *
 * All state is driven off the signal-backed {@link AddressService} cache so
 * the checkout page picks up mutations immediately.
 */
@Component({
  selector: 'gi-account-addresses',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './addresses.component.html',
  styleUrl: './addresses.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AccountAddressesComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly addressService = inject(AddressService);

  protected readonly addresses = this.addressService.addresses;
  protected readonly listLoading = this.addressService.loading;

  protected readonly mode = signal<Mode>('list');
  protected readonly editingId = signal<number | null>(null);
  protected readonly saving = signal<boolean>(false);
  protected readonly busyId = signal<number | null>(null);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly successMessage = signal<string | null>(null);

  protected readonly form = this.fb.nonNullable.group({
    addressType: this.fb.nonNullable.control<AddressType>('BOTH', { validators: [Validators.required] }),
    isDefault:   this.fb.nonNullable.control<boolean>(false),
    fullName:    ['', [Validators.maxLength(200)]],
    company:     ['', [Validators.maxLength(255)]],
    streetLine1: ['', [Validators.required, Validators.maxLength(255)]],
    streetLine2: ['', [Validators.maxLength(255)]],
    city:        ['', [Validators.required, Validators.maxLength(120)]],
    region:      ['', [Validators.maxLength(120)]],
    postalCode:  ['', [Validators.maxLength(32)]],
    countryCode: ['ET', [Validators.required, Validators.pattern(/^[A-Za-z]{2}$/)]],
    phone:       ['', [Validators.maxLength(64)]],
  });

  protected readonly hasAddresses = computed<boolean>(() => (this.addresses() ?? []).length > 0);

  // Ordered list — default first, then most-recently-updated.
  protected readonly orderedAddresses = computed<Address[]>(() => {
    const list = [...(this.addresses() ?? [])];
    list.sort((a, b) => {
      if (a.isDefault !== b.isDefault) return a.isDefault ? -1 : 1;
      return b.updatedAt.localeCompare(a.updatedAt);
    });
    return list;
  });

  // ---------------------------------------------------------------------------

  ngOnInit(): void {
    this.addressService.list().subscribe({
      error: (err: NormalizedApiError) => {
        this.errorMessage.set(err.message || 'Could not load your addresses.');
      },
    });
  }

  // --- Mode transitions -----------------------------------------------------

  protected openCreate(): void {
    this.clearBanners();
    const willBeDefault = (this.addresses() ?? []).length === 0;
    this.form.reset({
      addressType: 'BOTH',
      isDefault: willBeDefault,
      fullName: '',
      company: '',
      streetLine1: '',
      streetLine2: '',
      city: '',
      region: '',
      postalCode: '',
      countryCode: 'ET',
      phone: '',
    });
    this.editingId.set(null);
    this.mode.set('create');
  }

  protected openEdit(a: Address): void {
    this.clearBanners();
    this.form.reset({
      addressType: a.addressType,
      isDefault: a.isDefault,
      fullName: a.fullName ?? '',
      company: a.company ?? '',
      streetLine1: a.streetLine1,
      streetLine2: a.streetLine2 ?? '',
      city: a.city,
      region: a.region ?? '',
      postalCode: a.postalCode ?? '',
      countryCode: a.countryCode,
      phone: a.phone ?? '',
    });
    this.editingId.set(a.id);
    this.mode.set('edit');
  }

  protected cancelEdit(): void {
    this.editingId.set(null);
    this.mode.set('list');
    this.errorMessage.set(null);
  }

  // --- Submit ---------------------------------------------------------------

  protected submit(): void {
    if (this.saving()) return;
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.errorMessage.set(null);
    this.successMessage.set(null);
    const raw = this.form.getRawValue();

    if (this.mode() === 'create') {
      const payload: CreateAddressPayload = this.buildCreatePayload(raw);
      this.saving.set(true);
      this.addressService.create(payload).subscribe({
        next: () => {
          this.saving.set(false);
          this.successMessage.set('Address saved.');
          this.mode.set('list');
        },
        error: (err: NormalizedApiError) => {
          this.saving.set(false);
          this.errorMessage.set(err.message || 'Could not save that address.');
        },
      });
      return;
    }

    // edit
    const id = this.editingId();
    if (id === null) {
      this.errorMessage.set('Missing address id.');
      return;
    }
    const payload: UpdateAddressPayload = this.buildUpdatePayload(raw);
    this.saving.set(true);
    this.addressService.update(id, payload).subscribe({
      next: () => {
        this.saving.set(false);
        this.successMessage.set('Address updated.');
        this.mode.set('list');
        this.editingId.set(null);
      },
      error: (err: NormalizedApiError) => {
        this.saving.set(false);
        this.errorMessage.set(err.message || 'Could not update that address.');
      },
    });
  }

  private buildCreatePayload(raw: ReturnType<typeof this.form.getRawValue>): CreateAddressPayload {
    return {
      addressType: raw.addressType,
      isDefault: raw.isDefault,
      streetLine1: raw.streetLine1.trim(),
      city: raw.city.trim(),
      countryCode: raw.countryCode.toUpperCase(),
      ...(raw.fullName    ? { fullName:    raw.fullName.trim() }    : {}),
      ...(raw.company     ? { company:     raw.company.trim() }     : {}),
      ...(raw.streetLine2 ? { streetLine2: raw.streetLine2.trim() } : {}),
      ...(raw.region      ? { region:      raw.region.trim() }      : {}),
      ...(raw.postalCode  ? { postalCode:  raw.postalCode.trim() }  : {}),
      ...(raw.phone       ? { phone:       raw.phone.trim() }       : {}),
    };
  }

  private buildUpdatePayload(raw: ReturnType<typeof this.form.getRawValue>): UpdateAddressPayload {
    // For update we send every field — the backend treats blank as "clear"
    // and null as "keep". Trim strings; empty → undefined.
    const norm = (s: string): string | undefined => (s.trim() === '' ? undefined : s.trim());
    return {
      addressType: raw.addressType,
      isDefault: raw.isDefault,
      streetLine1: raw.streetLine1.trim(),
      city: raw.city.trim(),
      countryCode: raw.countryCode.toUpperCase(),
      fullName:    norm(raw.fullName),
      company:     norm(raw.company),
      streetLine2: norm(raw.streetLine2),
      region:      norm(raw.region),
      postalCode:  norm(raw.postalCode),
      phone:       norm(raw.phone),
    };
  }

  // --- Row actions ----------------------------------------------------------

  protected makeDefault(a: Address): void {
    if (a.isDefault || this.busyId() !== null) return;
    this.clearBanners();
    this.busyId.set(a.id);
    this.addressService.update(a.id, { isDefault: true }).subscribe({
      next: () => {
        this.busyId.set(null);
        this.successMessage.set('Default address updated.');
      },
      error: (err: NormalizedApiError) => {
        this.busyId.set(null);
        this.errorMessage.set(err.message || 'Could not update the default address.');
      },
    });
  }

  protected remove(a: Address): void {
    if (this.busyId() !== null) return;
    const label = a.streetLine1;
    const proceed = confirm(`Remove this address?\n\n${label}, ${a.city}`);
    if (!proceed) return;
    this.clearBanners();
    this.busyId.set(a.id);
    this.addressService.delete(a.id).subscribe({
      next: () => {
        this.busyId.set(null);
        this.successMessage.set('Address removed.');
      },
      error: (err: NormalizedApiError) => {
        this.busyId.set(null);
        this.errorMessage.set(err.message || 'Could not remove that address.');
      },
    });
  }

  private clearBanners(): void {
    this.errorMessage.set(null);
    this.successMessage.set(null);
  }

  // --- Formatting helpers ---------------------------------------------------

  protected addressLines(a: Address): string {
    const parts: string[] = [];
    if (a.streetLine2) parts.push(`${a.streetLine1}, ${a.streetLine2}`);
    else parts.push(a.streetLine1);
    const cityLine = [a.city, a.region, a.postalCode].filter(Boolean).join(', ');
    if (cityLine) parts.push(cityLine);
    parts.push(a.countryCode);
    return parts.join(' • ');
  }

  protected addressTypeLabel(t: AddressType): string {
    switch (t) {
      case 'BILLING':  return 'Billing';
      case 'SHIPPING': return 'Shipping';
      default:         return 'Shipping & Billing';
    }
  }
}
