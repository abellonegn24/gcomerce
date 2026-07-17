package com.gadaaistar.common;

/**
 * Enumeration of currencies supported by the platform at launch.
 * See Section 3 (Project Scope) of the delivery plan.
 */
public enum SupportedCurrency {
    USD("US Dollar", 2),
    EUR("Euro", 2),
    GBP("British Pound Sterling", 2);

    private final String displayName;
    private final int fractionDigits;

    SupportedCurrency(String displayName, int fractionDigits) {
        this.displayName = displayName;
        this.fractionDigits = fractionDigits;
    }

    public String displayName() {
        return displayName;
    }

    public int fractionDigits() {
        return fractionDigits;
    }
}
