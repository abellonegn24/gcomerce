export type AddressType = 'BILLING' | 'SHIPPING' | 'BOTH';

export interface Address {
  id: number;
  addressType: AddressType;
  isDefault: boolean;
  fullName: string | null;
  company: string | null;
  streetLine1: string;
  streetLine2: string | null;
  city: string;
  region: string | null;
  postalCode: string | null;
  /** ISO-3166-1 alpha-2, uppercase. */
  countryCode: string;
  phone: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CreateAddressPayload {
  addressType: AddressType;
  isDefault: boolean;
  fullName?: string;
  company?: string;
  streetLine1: string;
  streetLine2?: string;
  city: string;
  region?: string;
  postalCode?: string;
  countryCode: string;
  phone?: string;
}

/** Every field optional. Server treats null as "keep current". */
export type UpdateAddressPayload = Partial<CreateAddressPayload>;
