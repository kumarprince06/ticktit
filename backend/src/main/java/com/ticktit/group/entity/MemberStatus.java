package com.ticktit.group.entity;

/** JOINED until the payment window starts; then PENDING until they pay, PAID after. */
public enum MemberStatus {
    JOINED,
    PENDING,
    PAID
}
