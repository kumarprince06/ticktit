package com.ticktit.movie.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One seat in a screen's drawn layout. Its status for a particular show lives in ShowSeat. */
@Entity
@Table(name = "screen_seats")
@Getter
@Setter
@NoArgsConstructor
public class ScreenSeat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long screenId;

    @Column(nullable = false)
    private String seatNo;

    @Column(nullable = false)
    private String rowLabel;

    @Column(nullable = false)
    private int colNo;

    @Column(nullable = false)
    private int gridRow;

    @Column(nullable = false)
    private int gridCol;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SeatCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SeatType seatType = SeatType.NORMAL;

    @Column(nullable = false)
    private boolean isActive = true;
}
