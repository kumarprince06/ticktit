package com.ticktit.booking.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Money is in paise. Seat numbers like "H7". */
public final class BookingDtos {

    private BookingDtos() {
    }

    public record LockRequest(@NotNull UUID groupId,
                              @NotEmpty @Size(max = 20) List<@Pattern(regexp = "^[A-Za-z]{1,2}[0-9]{1,3}$") String> seats) {
    }

    public record UnlockRequest(@NotNull UUID groupId) {
    }

    public record AssignRequest(@NotEmpty @Size(max = 20) List<@Valid Assignment> assignments) {
    }

    public record Assignment(@NotNull String seatNo, @NotNull UUID userId) {
    }

    public record Person(UUID userId, String name, String avatarUrl) {
    }

    /** One seat: the ticket price (as shown by the cinema) and what is paid for it (ticket + fee + GST on fee). */
    public record SeatLine(String seatNo, String category, long ticketPricePaise, long payablePaise, Person assignee, boolean paid) {
    }

    /** paid: every seat of this person is paid for (by them, or by the organiser paying for all). */
    public record Share(Person member, List<String> seats, long sharePaise, long leftPaise, boolean paid) {
    }

    /**
     * mode: SPLIT / PAY_FOR_ALL once payment has started. youOwePaise: what the caller still has to pay right now
     * (their unpaid seats under Split Pay; everything left for the organiser under Pay For All).
     */
    public record BookingView(UUID id, String code, String status, String mode, Instant lockExpiresAt, Instant paymentDeadline,
                              long totalPaise, long paidPaise, long youOwePaise, boolean youAreLeader, String invoiceNo,
                              Instant confirmedAt, List<SeatLine> seats, List<Share> shares) {
    }
}
