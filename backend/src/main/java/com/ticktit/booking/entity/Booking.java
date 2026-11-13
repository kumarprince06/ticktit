package com.ticktit.booking.entity;

import com.ticktit.common.enums.ItemType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "bookings")
@Getter
@Setter
@NoArgsConstructor
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private UUID publicId = UUID.randomUUID();

    @Column(nullable = false, updatable = false)
    private String bookingCode;

    @Column(nullable = false, updatable = false)
    private Long groupId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private ItemType itemType;

    @Column(nullable = false, updatable = false)
    private Long itemId;

    /** Change only through transitionTo. */
    @Setter(AccessLevel.NONE)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingStatus status = BookingStatus.LOCKED;

    @Enumerated(EnumType.STRING)
    private PaymentMode mode;

    @Column(nullable = false)
    private long totalPaise;

    @Column(nullable = false)
    private long paidPaise;

    @Column(nullable = false)
    private Instant lockExpiresAt;

    private Instant paymentDeadline;

    /** {"PRIME": 28000}: what each category cost when the seats were locked. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Long> priceSnapshot;

    /** Optimistic lock: two writers (e.g. scheduler and payment webhook) can't both move the same booking. */
    @Version
    @Column(nullable = false)
    private int version;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    /** The only way to change status: refuses moves the state machine doesn't allow (architecture.md §3). */
    public void transitionTo(BookingStatus next) {
        if (!status.canMoveTo(next)) {
            throw new IllegalStateException("Booking " + bookingCode + " can't go from " + status + " to " + next);
        }
        status = next;
    }

    /** LOCKED but past its 5 minutes: the seats are already free in Redis, the scheduler just hasn't marked it yet. */
    public boolean isLockExpired() {
        return status == BookingStatus.LOCKED && !lockExpiresAt.isAfter(Instant.now());
    }
}
