package com.ticktit.booking.service;

/**
 * PAYMENT.md, per seat: the ticket price already includes GST (18% above ₹100, 5% at ₹100 or below),
 * plus a ₹20 convenience fee with 18% GST on top.
 * Example: ₹250 ticket = 21186 base + 3814 GST; fee 2000 + 360 GST; payable 27360 (₹273.60).
 */
public final class Pricing {

    public static final long FEE_PAISE = 2000;
    public static final int FEE_GST_RATE_BP = 1800;
    private static final long LOW_PRICE_LIMIT_PAISE = 10000;   // ₹100

    private Pricing() {
    }

    public static int ticketGstRateBp(long pricePaise) {
        return pricePaise > LOW_PRICE_LIMIT_PAISE ? 1800 : 500;
    }

    /** The part of a GST-inclusive price that is not tax. */
    public static long basePaise(long pricePaise) {
        int rate = ticketGstRateBp(pricePaise);
        return Math.round(pricePaise * 10000.0 / (10000 + rate));
    }

    public static long feeGstPaise() {
        return Math.round(FEE_PAISE * FEE_GST_RATE_BP / 10000.0);
    }

    public static long payablePaise(long pricePaise) {
        return pricePaise + FEE_PAISE + feeGstPaise();
    }
}
