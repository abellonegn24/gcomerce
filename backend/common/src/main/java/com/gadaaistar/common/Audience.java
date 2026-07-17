package com.gadaaistar.common;

/**
 * Which API surface a JWT was minted for. Storefront tokens carry customer identity,
 * admin tokens carry admin-user identity. The two token flows are independent.
 */
public enum Audience {
    STOREFRONT,
    ADMIN
}
