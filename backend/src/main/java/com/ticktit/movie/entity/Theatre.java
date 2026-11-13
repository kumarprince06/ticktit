package com.ticktit.movie.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.List;

@Entity
@Table(name = "theatres")
@Getter
@Setter
@NoArgsConstructor
public class Theatre {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long cityId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String slug;

    @Column(nullable = false)
    private String addressLine;

    @Column(nullable = false)
    private String area;

    @Column(nullable = false)
    private String pincode;

    private BigDecimal lat;
    private BigDecimal lng;
    private String gstin;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false, columnDefinition = "text[]")
    private List<String> amenities = List.of();

    @Column(nullable = false)
    private boolean cancellationAllowed;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TheatreStatus status = TheatreStatus.ACTIVE;
}
