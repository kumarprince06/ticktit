package com.ticktit.booking.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One seat in a booking: who sits there and exactly what it costs (PAYMENT.md). Prices are frozen at lock time. */
@Entity
@Table(name = "booking_units")
@Getter
@Setter
@NoArgsConstructor
public class BookingUnit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private Long bookingId;

    @Column(nullable = false, updatable = false)
    private String unitRef;

    @Column(nullable = false, updatable = false)
    private String category;

    private Long assigneeUserId;

    @Column(nullable = false)
    private long ticketPricePaise;

    @Column(nullable = false)
    private long ticketBasePaise;

    @Column(nullable = false)
    private long ticketGstPaise;

    @Column(nullable = false)
    private int ticketGstRateBp;

    @Column(nullable = false)
    private long feePaise;

    @Column(nullable = false)
    private long feeGstPaise;

    /** What the person sitting here pays: ticket (GST inside) + convenience fee + GST on the fee. */
    public long payablePaise() {
        return ticketPricePaise + feePaise + feeGstPaise;
    }
}
