import { api } from "./client";

/** Mirrors the backend's BookingDtos. Money in paise. */
export type Person = { userId: string; name: string; avatarUrl: string | null };
export type SeatLine = { seatNo: string; category: string; ticketPricePaise: number; payablePaise: number; assignee: Person | null; paid: boolean };
export type Share = { member: Person; seats: string[]; sharePaise: number; leftPaise: number; paid: boolean };
export type BookingView = {
  id: string;
  code: string;
  status: "LOCKED" | "PAYMENT_PENDING" | "CONFIRMED" | "RELEASED" | "FAILED" | "CANCELLED";
  mode: "SPLIT" | "PAY_FOR_ALL" | null;
  lockExpiresAt: string;
  paymentDeadline: string | null;
  totalPaise: number;
  paidPaise: number;
  /** what you still have to pay right now */
  youOwePaise: number;
  youAreLeader: boolean;
  invoiceNo: string | null;
  confirmedAt: string | null;
  seats: SeatLine[];
  shares: Share[];
};

/**
 * One Idempotency-Key per user action (ADR-010). The api client re-sends the same headers when it retries,
 * so a retry of this action reuses the key; a new click makes a new key.
 */
export const newActionKey = () =>
  crypto.randomUUID?.() ?? `${Date.now().toString(36)}-${Math.random().toString(36).slice(2)}-${Math.random().toString(36).slice(2)}`;

const idem = () => ({ "Idempotency-Key": newActionKey() });

export const bookingApi = {
  lock: (groupId: string, seats: string[]) =>
    api<BookingView>("/bookings/lock", { method: "POST", body: { groupId, seats }, auth: true, headers: idem() }),
  unlock: (groupId: string) =>
    api("/bookings/unlock", { method: "POST", body: { groupId }, auth: true, headers: idem() }),
  assign: (bookingId: string, assignments: { seatNo: string; userId: string }[]) =>
    api<BookingView>(`/bookings/${bookingId}/assign`, { method: "POST", body: { assignments }, auth: true, headers: idem() }),
  /** null when the group has no current booking (204). */
  current: async (groupId: string) => (await api<BookingView | undefined>(`/groups/${groupId}/booking`, { auth: true })) ?? null,
};
