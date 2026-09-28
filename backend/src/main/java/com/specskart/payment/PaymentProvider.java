package com.specskart.payment;

/** Abstraction over a hosted-checkout payment gateway. */
public interface PaymentProvider {

    String name();

    /**
     * Begin a payment for an order. Returns the URL to redirect the shopper to, plus the
     * provider reference we store on the order and later verify.
     */
    Payment start(PaymentRequest request);

    /**
     * Confirm with the gateway that a reference is actually paid, in full and in the right
     * currency. Never trust the callback alone, and never trust "successful" alone either.
     */
    boolean verify(String providerRef, long expectedMinor, String currency);

    record PaymentRequest(String orderNo, long amountMinor, String currency,
                          String customerName, String customerEmail, String customerPhone,
                          String redirectUrl) {}

    record Payment(String providerRef, String checkoutUrl) {}
}
