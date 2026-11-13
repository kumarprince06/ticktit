import { newActionKey } from "./bookings";
import { api } from "./client";

/** Mirrors the backend's PaymentDtos. Amounts in paise, computed by the server. */
export type Checkout = {
  paymentId: string;
  gateway: string;
  keyId: string;
  orderId: string;
  amountPaise: number;
  currency: string;
  bookingCode: string;
  description: string;
  paymentDeadline: string;
  prefill: { name: string; email: string; contact: string | null };
};

export const paymentApi = {
  /** mode only when the organiser starts payment, or to pay for everyone. One Idempotency-Key per Pay tap. */
  checkout: (bookingId: string, mode?: "SPLIT" | "PAY_FOR_ALL") =>
    api<Checkout>("/payments/checkout", {
      method: "POST", body: { bookingId, mode }, auth: true, headers: { "Idempotency-Key": newActionKey() },
    }),
  verify: (orderId: string, paymentId: string, signature: string) =>
    api<{ paymentStatus: string; bookingStatus: string }>("/payments/verify", {
      method: "POST", body: { orderId, paymentId, signature }, auth: true,
    }),
};

type RazorpayResponse = { razorpay_payment_id: string; razorpay_order_id: string; razorpay_signature: string };
type RazorpayFailure = { error: { description: string; reason?: string } };
type RazorpayInstance = { open: () => void; on: (event: "payment.failed", cb: (r: RazorpayFailure) => void) => void };
declare global {
  interface Window {
    Razorpay?: new (options: Record<string, unknown>) => RazorpayInstance;
  }
}

let scriptLoading: Promise<void> | null = null;

/** Razorpay's checkout script, loaded once, only when someone actually pays. */
function loadCheckoutScript(): Promise<void> {
  if (window.Razorpay) return Promise.resolve();
  scriptLoading ??= new Promise<void>((resolve, reject) => {
    const script = document.createElement("script");
    script.src = "https://checkout.razorpay.com/v1/checkout.js";
    script.onload = () => resolve();
    script.onerror = () => {
      scriptLoading = null;
      reject(new Error("Couldn't load the payment window. Check your connection and try again."));
    };
    document.body.appendChild(script);
  });
  return scriptLoading;
}

export type PayResult = { outcome: "paid" } | { outcome: "closed" } | { outcome: "failed"; message: string };

/**
 * Opens Razorpay Checkout for a server-made order. On success the server verifies the signature and asks Razorpay
 * itself before counting the money; the browser's word alone never counts.
 */
export async function payWithRazorpay(checkout: Checkout): Promise<PayResult> {
  await loadCheckoutScript();
  return new Promise<PayResult>((resolve) => {
    let failure: string | null = null;
    const rzp = new window.Razorpay!({
      key: checkout.keyId,
      order_id: checkout.orderId,
      amount: checkout.amountPaise,
      currency: checkout.currency,
      name: "Ticktit",
      description: checkout.description,
      prefill: { name: checkout.prefill.name, email: checkout.prefill.email, contact: checkout.prefill.contact ?? undefined },
      theme: { color: "#5b3df5" },
      handler: async (r: RazorpayResponse) => {
        try {
          await paymentApi.verify(r.razorpay_order_id, r.razorpay_payment_id, r.razorpay_signature);
          resolve({ outcome: "paid" });
        } catch (e) {
          resolve({ outcome: "failed", message: e instanceof Error ? e.message : "We couldn't confirm the payment yet." });
        }
      },
      modal: {
        // closed without paying (or after a failure): the order stays open, Pay again reuses it
        ondismiss: () => resolve(failure ? { outcome: "failed", message: failure } : { outcome: "closed" }),
      },
    });
    rzp.on("payment.failed", (r) => {
      failure = r.error.description || "The payment didn't go through.";
    });
    rzp.open();
  });
}
