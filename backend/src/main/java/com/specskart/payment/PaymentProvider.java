package com.specskart.payment;

/** Abstraction over a hosted-checkout payment gateway. */
public interface PaymentProvider {

    String name();

    /**
     * Begin a payment for an order. Returns the URL to redirect the shopper to, plus the
     * provider reference we store on the order and later verify.
     */
    Payment start(PaymentRequest request);

    /** Confirm with the gateway that a reference is actually paid. Never trust the callback alone. */
    boolean verify(String providerRef);

    record PaymentRequest(String orderNo, long amountMinor, String currency,
                          String customerName, String customerEmail, String customerPhone,
                          String redirectUrl) {}

    record Payment(String providerRef, String checkoutUrl) {}
}
