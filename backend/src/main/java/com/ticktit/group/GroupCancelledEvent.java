package com.ticktit.group;

/** Published when an organiser cancels a group, so other modules (booking) can let go of what it held. */
public record GroupCancelledEvent(long groupId) {
}
