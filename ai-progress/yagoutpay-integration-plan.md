# YagoutPay Integration — Execution Plan

**Project:** Gadaa Istar Export E-Commerce Platform (`gcomerce`)
**Document version:** 1.0
**Date:** 2026-10-22
**Status:** Implemented — YP-1 through YP-6 (code + unit tests) complete;
YP-0 prerequisites and UAT e2e remain open external dependencies
**References:**
- `ai-progress/YagoutPay Integration Document.pdf`
- `ai-progress/Nodejs YP Integration/` (working Node.js reference implementation)
- `ai-progress/yagoutpay-extracted.txt` (plain-text extraction of the PDF)

---

## 1. Objective

Add **YagoutPay** as a third checkout payment option alongside **MPGS** (Mastercard
Hosted Checkout) and **CyberSource** (Secure Acceptance). YagoutPay is a domestic
Ethiopian aggregator that processes **ETB** payments via cards and local wallets
(telebirr, mpesa, cbe birr, kacha), enabling Ethiopian customers to pay in local
currency through familiar channels.

---

## 2. Gateway Mechanics (from the integration document)

### 2.1 Integration methods offered

| Method | Transport | Decision |
|---|---|---|
| **Aggregator Hosted (Non-Seamless)** | Browser form-POST to YagoutPay hosted page; result POSTed back to merchant URLs | **Selected** — same UX model as CyberSource / MPGS hosted checkout |
| **API Integration** (`channel=API`, JSON) | Server→server, wallet-direct (telebirr, mpesa, cbebirr, kacha) | Deferred — possible future enhancement for wallet-direct checkout |

### 2.2 Aggregator Hosted request format

Browser POSTs three fields:

```
POST https://uatcheckout.yagoutpay.com/ms-transaction-core-1-0/paymentRedirection/checksumGatewayPage
Content-Type: application/x-www-form-urlencoded

me_id=<merchantId, plaintext>
merchant_request=<AES-256-CBC ciphertext, base64>
hash=<AES-256-CBC of sha256hex("me_id~order_no~amount~country~currency"), base64>
```

Production post URL is supplied by YagoutPay upon go-live.

**Crypto:** AES-CBC, key = `base64decode(encryption_key)` (16/24/32 bytes →
AES-128/192/256), fixed IV `0123456789abcdef` (ASCII), PKCS7 padding,
base64 output.

**`merchant_request` plaintext:** nine sections joined by `~`; fields within a
section joined by `|`. Layout proven by the working Node.js sample:

```
txn_details   : ag_id|me_id|order_no|amount|country|currency|txn_type|success_url|failure_url|channel
                → yagout|<MID>|<orderNo>|<amount>|ETH|ETB|SALE|<successUrl>|<failureUrl>|WEB
pg_details    : pg_id|paymode|scheme|wallet_type                    → "|||"  (blank for hosted)
card_details  : card_no|exp_month|exp_year|cvv|card_name            → "||||" (blank for hosted)
cust_details  : cust_name|email_id|mobile_no|unique_id|is_logged_in → e.g. "Name|a@b.com|09…||Y"
bill_details  : bill_address|bill_city|bill_state|bill_country|bill_zip
ship_details  : ship_address|ship_city|ship_state|ship_country|ship_zip|ship_days|address_count
item_details  : item_count|item_value|item_category
upi_details   : ""  (not required)
other_details : udf_1|udf_2|udf_3|udf_4|udf_5
```

Field rules of note: `country=ETH`, `currency=ETB`, `txn_type=SALE`,
`channel=WEB`, `amount` = `Numeric(10,2)` plain decimal (`"150.00"`),
`order_no` alphanumeric ≤70 chars, **unique per transaction request**.
`cust_name` / `email_id` / `mobile_no` are marked required for fraud
protection.

### 2.3 Response format

YagoutPay POSTs the result to `success_url` or `failure_url` as a form-POST
carried by the customer's browser (no server-to-server webhook exists).
Fields are **each separately AES-256-CBC encrypted** except `me_id`:

```
txn_response, pg_details, txn_details, other_details, fraud_details, me_id
```

Decrypted `txn_response` (pipe-delimited):

```
ag_id|me_id|order_no|amount|country|currency|txn_date|txn_time|ag_ref|pg_ref|status|res_code|res_message
e.g. yagout|202505060003|56212|1.00|ETH|ETB|2025-04-30|09:56:23|2058501746006825776|AG_20250430_70701b7a0bffdb44a880|Successful|0|Successful
```

Success criterion: `status == "Successful"` **and** `res_code == "0"`.
`ag_ref` / `pg_ref` are the gateway's references → stored as our
`payment_reference`.

### 2.4 Debug APIs (useful during bring-up)

| API | URL | Use |
|---|---|---|
| Encrypt | `POST /ms-transaction-core-1-0/othersRedirection/encryption` (header `me_id`, plain-text body) | Verify our plaintext encrypts identically |
| Decrypt | `POST /ms-transaction-core-1-0/othersRedirection/decryption` | Verify their ciphertext decrypts with our key handling |

### 2.5 Hard constraints

1. **Registered-domain enforcement** — requests only accepted from merchant
   domains registered with YagoutPay; localhost/unregistered hosts fail with
   "Invalid Referral URL". **Blocking external dependency** (§4, YP-0).
2. **Currency** — the published doc lists ETB only, but per direct discussion
   with YagoutPay they will also enable international payments (USD etc.).
   **Design is currency-agnostic**: the payload takes `order.currency`
   verbatim; an optional `supported-currencies` allowlist config exists as an
   escape hatch until YagoutPay confirms the rollout.
3. **No webhook** — the browser-return POST is the only result channel.
4. **No HMAC/signature on the response** — integrity rests on AES + shared
   key; we validate decrypted fields strictly (me_id, order_no, amount,
   currency, status).
5. **No refunds API documented** — refunds are out-of-band → existing MANUAL
   refund path.
6. **`order_no` must be unique per request** — payment retries need unique
   values (see §3.4).

---

## 3. Design Decisions

### 3.1 Callback architecture — backend "return handler" endpoints

YagoutPay's result arrives as a **form-POST to the browser**, not a webhook.
An Angular route cannot read a POST body, and there is no server-side channel
to be authoritative. Therefore:

- `success_url` = `POST {api-base}/api/storefront/v1/payment/yagoutpay/return`
- `failure_url` = `POST {api-base}/api/storefront/v1/payment/yagoutpay/failure`

Both are **public** endpoints (no JWT — the gateway/browser carries no bearer
token). Each decrypts `txn_response`, validates it, mutates the order via the
existing `OrderService.markPaid` / `markPaymentFailed`, then issues a
**303 See Other** redirect to the Angular pages:

- success → `{storefront}/checkout/return?order=<uuid>&provider=YAGOUTPAY`
- failure → `{storefront}/checkout/cancel?order=<uuid>&provider=YAGOUTPAY`

The existing `checkout-return` page already polls `GET /me/orders/{uuid}` and
flips live to PAID — it works unchanged.

### 3.2 Currency handling

Per stakeholder discussion, YagoutPay will support international currencies
beyond ETB (USD etc.). The integration is therefore **currency-agnostic**:
`order.currency` is passed through to the payload and `PaymentMethodsResource`
lists `YAGOUTPAY` whenever the gateway is configured. An optional
`gadaa.yagoutpay.supported-currencies` allowlist (empty = all) lets ops
restrict the option per-currency if YagoutPay's international rollout lands
partial.

### 3.3 Field sourcing

| YagoutPay field | Source |
|---|---|
| `order_no` | `invoiceNumber` (attempt 1); `invoiceNumber + "-" + attempt` on retries |
| `amount` | `order.grandTotal` at scale 2 |
| `cust_name` / `email_id` / `mobile_no` | customer record |
| `bill_*` | order's bill-to address snapshot |
| `ship_*` | order's ship-to address snapshot |
| `item_count` / `item_value` / `item_category` | order lines |
| `udf_1` | `order.uuid` (identity cross-check on return) |
| `success_url` / `failure_url` | §3.1 endpoints on the public API base URL |

### 3.4 Payment-retry uniqueness

`checkout_order` gains `payment_attempt INT NOT NULL DEFAULT 0`
(Flyway V21). `buildRedirectPayload` increments it per request; attempt 1 uses
bare `invoiceNumber`, later attempts append `-<n>`. `udf_1=order.uuid` lets the
return handler locate the order regardless of suffix.

### 3.5 Refunds

YagoutPay documents no refund API → always take the existing **MANUAL** refund
path for `paymentMethod == "YAGOUTPAY"` orders (row persisted with
`gateway_provider=YAGOUTPAY`, `status=SUCCEEDED`). Admin UI forces the
"record as manual" checkbox on and shows a hint.

### 3.6 Frontend reuse

`fetchPaymentPayloadAndRedirect` in `checkout.component.ts` already branches:
`MPGS` → Checkout.js lightbox; **else → `autoPostToGateway`** (hidden
auto-submitting form). `provider == "YAGOUTPAY"` rides the existing
form-POST branch verbatim — the three fields `me_id` / `merchant_request` /
`hash` need no ordering guarantees.

---

## 4. Work Breakdown

### YP-0 — Prerequisites (external, blocking)

- [ ] Obtain test credentials from YagoutPay: `merchant-id`,
      `encryption-key` (base64), `aggregator-id` (expected `yagout`)
- [ ] **Register callback domain(s)** with YagoutPay — staging + production
      API hosts (e.g. `api.gedaaistar.et`). Required to clear the
      "Invalid Referral URL" domain check.
- [ ] Confirm UAT simulator flow for forced success/failure cases
- [ ] Obtain production post URL + prod credentials (post-UAT sign-off)

### YP-1 — Backend: api-storefront

New files under `com.gadaaistar.storefront.payment`:

- [x] `YagoutPayConfig` — typed wrapper for `gadaa.yagoutpay.*`
      (`enabled`, `merchant-id`, `encryption-key`, `aggregator-id`,
      `gateway-url`, `callback-base-url`, `country`,
      `supported-currencies`, `storefront-url`) + `isConfigured()` /
      `supportsCurrency()`. Mirrors `MpgsConfig`.
- [x] `YagoutPayCrypto` — JCA `AES/CBC/PKCS5Padding`, fixed IV
      `0123456789abcdef`, key = base64-decoded merchant key (16/24/32 bytes
      supported per the Node sample's `getAesAlgorithm`), `sha256Hex()`
      helper. No new dependency needed.
- [x] `YagoutPayService` —
  - `buildRedirectPayload(order, attempt)` → `{endpoint, fields{me_id,
    merchant_request, hash}}`; assembles the 9-section pipe payload
    (§2.2), computes the tilde-delimited sha256 hash, fills
    cust/bill/ship/item details from the order, `udf_1 = order.uuid`,
    sanitizes `|`/`~` out of field values
  - `parseReturn(formParams)` → decrypt + split `txn_response`, validate
    `me_id` + field count, extract `udf_1` from `other_details`, return
    typed `YagoutResult` (`null` on any validation failure — caller
    mutates nothing)
- [x] `YagoutPayReturnResource` — `POST .../payment/yagoutpay/return` and
      `.../failure`; `@Consumes(FORM_URLENCODED)`, `@RateLimit` (60/min/IP);
      idempotent (skip mutation when order is not `PENDING_PAYMENT`, same as
      `CyberSourceWebhookResource`); order resolution via `udf_1` → exact
      invoice match → attempt-suffix strip; strict amount/currency match +
      `paymentMethod == YAGOUTPAY` guard; 303-redirect per §3.1;
      decryption/lookup failures → 303 to cancel page with `paymentError=1`
      (log detail, echo nothing); **the failure URL never marks paid**
- [x] `OrderService` — accepts `"YAGOUTPAY"` in `setPaymentMethod`
      (`isConfigured()` + `supportsCurrency` checks →
      `PAYMENT_METHOD_CURRENCY_MISMATCH`) and `createFromCart`
      (supported-method + currency validation — also closed the hole where
      an unrecognized method silently became a CyberSource redirect);
      `registerPaymentAttempt()` bumps the counter per attempt
- [x] `OrderResource.paymentPayload` — `YAGOUTPAY` branch returning
      `PaymentPayload("YAGOUTPAY", postUrl, {me_id, merchant_request, hash})`
- [x] `PaymentMethodsResource` — lists `YAGOUTPAY` when configured; optional
      `supported-currencies` allowlist gates it on the caller's cart
      currency (empty allowlist = all currencies, per §3.2). The
      `currencies` DTO field was dropped — unnecessary while ungated.
- [x] Flyway `V21__yagoutpay.sql` —
      `ALTER TABLE checkout_order ADD COLUMN payment_attempt INT NOT NULL DEFAULT 0`

### YP-2 — Backend: api-admin

- [x] `RefundService` — `paymentProvider == "YAGOUTPAY"` → `callGateway` is
      forced false (no documented refund API); the audit row records
      `gateway_provider=YAGOUTPAY` rather than a generic `MANUAL`
- [x] `AdminOrderDetail` already surfaces `paymentProvider` /
      `paymentMethod` — verified, renders `YAGOUTPAY` raw

### YP-3 — Frontend: storefront

- [x] No code changes required — the payment picker renders whatever
      `PaymentMethodsResource` returns, `fetchPaymentPayloadAndRedirect`
      auto-POSTs every non-MPGS provider (covers `YAGOUTPAY`'s 3 fields),
      and `checkout-return` polls order status for non-MPGS providers.
      `order-detail.component`'s "Continue payment" flow shares the same
      branches.
- [x] `checkout-return` / `checkout-cancel` accept `provider=YAGOUTPAY`
      (provider is display/telemetry only; polling is provider-agnostic)
- ~~`PaymentMethodOption.currencies`~~ — dropped (see YP-1)

### YP-4 — Frontend: admin

- [x] Order detail — provider value already renders; `isYagoutPayOrder`
      computed added
- [x] Refund modal — `manual` pre-ticked + checkbox locked for YagoutPay
      orders, `submitRefund` forces `manual:true`, info banner explains
      the out-of-band requirement (`gi-order__banner--info` added)

### YP-5 — Config, infra, docs

- [x] `application.properties` (api-storefront): `gadaa.yagoutpay.*` keys;
      `%dev` enables with the published UAT test merchant; `%test` leaves
      `enabled=false`
- [x] `.env.example` + `infra/docker-compose.yml`: `YAGOUTPAY_*` env vars
      on api-storefront
- [x] `progress.txt` — Phase 7 entry
- [x] `docs/Test-Access-Payloads.txt` — scope matrix item 12 + §3.12 for
      the two public return endpoints (merchant key out-of-band)
- [x] `ai-progress/INSA-Web-Application-Security-Audit-Documentation.md` —
      integrations table (CyberSource + YagoutPay rows), scope table item 6,
      out-of-scope + SAQ-A wording generalized to all hosted gateways
- [x] `README.md` — payment line is "MPGS + CyberSource + YagoutPay"

### YP-6 — Testing

- [x] `YagoutPayCryptoTest` — encrypt/decrypt round-trips incl. the doc's
      printed plaintext forms; AES-128/192/256 key lengths; malformed-key
      + wrong-key failures; tilde-vs-pipe hash delimiter guard.
      *Note: the doc's printed sample ciphertext is inconsistent with its
      own printed key (bad padding under both JCA and openssl) — exact
      wire-vector validation deferred to YagoutPay's `/decryption` debug
      API during UAT.*
- [x] `YagoutPayServiceTest` — 9-section payload layout (byte-structure
      asserts), real customer/address/item data sourcing, attempt suffix
      on `order_no`, delimiter sanitization, `not-configured` +
      currency-mismatch exceptions, all `parseReturn` guards
- [x] `YagoutPayReturnResourceTest` — fabricated results via mocked
      service: success → `markPaid` + return redirect; decline/failure →
      `markPaymentFailed` + cancel; "Successful" on the failure URL →
      still failure; idempotent replay; amount/currency mismatch → no
      mutation; wrong-provider order → no mutation; unknown order →
      error redirect; `udf_1` → invoice fallback chain
- [ ] UAT e2e (after domain registration): real hosted checkout, success +
      decline paths, abandon → sweeper still releases reservations at TTL,
      crypto cross-check via `/othersRedirection/decryption` debug API

---

## 5. Configuration Keys (new)

| Key | Env var | Default | Purpose |
|---|---|---|---|
| `gadaa.yagoutpay.enabled` | `GADAA_YAGOUTPAY_ENABLED` | `false` | Feature flag |
| `gadaa.yagoutpay.merchant-id` | `GADAA_YAGOUTPAY_MERCHANT_ID` | — | `me_id` |
| `gadaa.yagoutpay.encryption-key` | `GADAA_YAGOUTPAY_ENCRYPTION_KEY` | — | base64 AES key |
| `gadaa.yagoutpay.aggregator-id` | `GADAA_YAGOUTPAY_AGGREGATOR_ID` | `yagout` | `ag_id` |
| `gadaa.yagoutpay.gateway-url` | `GADAA_YAGOUTPAY_GATEWAY_URL` | UAT URL | form post target |
| `gadaa.yagoutpay.callback-base-url` | `GADAA_YAGOUTPAY_CALLBACK_BASE_URL` | — | public API origin for success/failure URLs |
| `gadaa.yagoutpay.country` | `GADAA_YAGOUTPAY_COUNTRY` | `ETH` | payload country |
| `gadaa.yagoutpay.currency` | `GADAA_YAGOUTPAY_CURRENCY` | `ETB` | payload currency |

Secrets go to Vault in staging/production per existing convention.

---

## 6. Error Codes (new)

| Code | When |
|---|---|
| `PAYMENT_METHOD_CURRENCY_MISMATCH` | YagoutPay selected on a non-ETB order |
| `YAGOUTPAY_DECRYPT_FAILED` | Return POST undecryptable (logged, never echoed) |
| `YAGOUTPAY_ORDER_MISMATCH` | `order_no`/`udf_1` doesn't resolve to a live order |

---

## 7. Security Notes

- Return endpoints are **public** — no JWT. Integrity comes from
  AES-256-CBC decryption with the shared merchant key + strict field
  validation (`me_id`, `order_no`, `amount`, `currency`, `country`).
- Idempotent mutation: only `PENDING_PAYMENT` orders are transitioned;
  replays redirect without side effects (mirrors the CyberSource handler).
- Rate-limit the return endpoints (brute-force ciphertext is a decryption
  DoS vector).
- Never log decrypted payloads at INFO — `DEBUG` at most, and never log
  `fraud_details` content beyond the res_code/res_message pair.
- Encryption key + merchant ID are secrets: env vars in dev, Vault in prod.

---

## 8. Effort Estimate (relative sizing, not dates)

| Phase | Size | Notes |
|---|---|---|
| YP-0 | External | Longest lead — starts immediately |
| YP-1 | **M** | Only genuinely new code (~300–400 lines): crypto + payload builder + return resource |
| YP-2 | XS | Refund gating |
| YP-3 | S | Picker option + provider label; redirect path reused |
| YP-4 | XS | Badge + refund hint |
| YP-5 | S | Config + doc updates |
| YP-6 | M | Crypto vectors + return-endpoint integration tests + UAT |

The implementation is small because the payment seams were generalised for
MPGS: `order.paymentMethod`, `payment-payload` branching,
`markPaid`/`markPaymentFailed`, the MANUAL refund path, and the storefront
`autoPostToGateway` all already exist.

---

## 9. Risks

| Risk | Impact | Mitigation |
|---|---|---|
| Domain registration pending | Cannot test e2e; requests rejected with "Invalid Referral URL" | Start YP-0 now; develop against crypto debug APIs meanwhile |
| No webhook / no status-query API | Customer who pays then closes the tab before returning leaves the order unpaid-but-charged | Sweeper keeps 60-min TTL; raise with YagoutPay whether a server-side status query exists (not documented); worst case ops reconcile manually |
| ETB-only on a USD-first store | YagoutPay unreachable until an ETB price list exists | Requires stakeholder decision (§10) |
| Duplicate `order_no` on retry | Gateway may reject repeated order numbers | `payment_attempt` suffix design (§3.4) |
| Response has no HMAC | Forgery only possible with the key, but validation must be strict | §7 validation rules; amount must match `grandTotal` exactly |

---

## 10. Open Questions for Stakeholders

1. Should the storefront support **ETB carts** (new ETB price list /
   multi-currency) so YagoutPay is selectable, or is YagoutPay only for a
   domestic-customer segment?
2. Does YagoutPay offer a **server-side transaction status query** API for
   the hosted flow (not in the document)? Needed to close the
   paid-but-never-returned gap.
3. Which **wallets** should be enabled on the YagoutPay merchant account
   (telebirr / mpesa / cbe birr / kacha)?
4. Confirm the **registered callback domain(s)** — production
   `api.gedaaistar.et` plus a staging host.
5. Refund operating procedure — who performs out-of-band YagoutPay refunds
   and how are they reconciled to `order_refund` rows?

---

## 11. Rollout

1. Merge behind `gadaa.yagoutpay.enabled=false` — zero behaviour change.
2. Configure UAT credentials + registered staging domain; run YP-6.
3. Flip staging flag, run UAT acceptance with ops.
4. Prod credentials + domain registration → enable in prod.
5. Update INSA scope doc + Test-Access-Payloads before the audit window.
