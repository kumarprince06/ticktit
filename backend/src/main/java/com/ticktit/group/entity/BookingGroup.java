package com.ticktit.group.entity;

import com.ticktit.common.enums.ItemType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/** Table "groups". Named BookingGroup because GROUP is a reserved word in JPQL. */
@Entity(name = "BookingGroup")
@Table(name = "groups")
@Getter
@Setter
@NoArgsConstructor
public class BookingGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private UUID publicId = UUID.randomUUID();

    @Column(nullable = false, updatable = false)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private ItemType itemType;

    @Column(nullable = false, updatable = false)
    private Long itemId;

    @Column(nullable = false)
    private Long leaderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GroupStatus status = GroupStatus.OPEN;

    @Column(nullable = false)
    private int maxMembers = 8;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;
}
