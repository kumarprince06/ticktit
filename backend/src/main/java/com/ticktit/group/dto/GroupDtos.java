package com.ticktit.group.dto;

import com.ticktit.common.inventory.InventoryProvider.ItemSummary;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;
import java.util.UUID;

public final class GroupDtos {

    private GroupDtos() {
    }

    public record CreateGroupRequest(@NotNull @Positive Long showId) {
    }

    public record GroupCreated(UUID groupId, String code, String shareLink, String whatsappShareUrl) {
    }

    /** Public, before joining: what the group is for and how full it is. No names, no personal data. */
    public record GroupPreview(String code, String status, int memberCount, int maxMembers, ItemSummary item) {
    }

    public record MemberView(UUID userId, String name, String avatarUrl, String role, String status, boolean you) {
    }

    /** For members only. */
    public record GroupView(UUID id, String code, String status, int maxMembers, boolean youAreLeader, ItemSummary item,
                            List<MemberView> members, String shareLink, String whatsappShareUrl) {
    }

    public record GroupCard(UUID id, String code, String status, int memberCount, boolean youAreLeader,
                            ItemSummary item) {
    }
}
