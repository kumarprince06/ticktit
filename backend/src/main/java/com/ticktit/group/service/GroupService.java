package com.ticktit.group.service;

import com.ticktit.auth.service.UserDirectory;
import com.ticktit.auth.service.UserDirectory.UserCard;
import com.ticktit.common.enums.ErrorCode;
import com.ticktit.common.enums.ItemType;
import com.ticktit.common.exception.ApiException;
import com.ticktit.common.inventory.InventoryProvider;
import com.ticktit.common.inventory.InventoryProvider.ItemSummary;
import com.ticktit.group.dto.GroupDtos.GroupCard;
import com.ticktit.group.dto.GroupDtos.GroupCreated;
import com.ticktit.group.dto.GroupDtos.GroupPreview;
import com.ticktit.group.dto.GroupDtos.GroupView;
import com.ticktit.group.dto.GroupDtos.MemberView;
import com.ticktit.group.entity.BookingGroup;
import com.ticktit.group.entity.GroupMember;
import com.ticktit.group.entity.GroupStatus;
import com.ticktit.group.entity.MemberRole;
import com.ticktit.group.repository.GroupMemberRepository;
import com.ticktit.group.repository.GroupRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Groups (PRD F4): a leader forms a group for a show, shares one link, friends join. Seats come later
 * (booking module). Members only ever see groups they belong to; outsiders get the public preview.
 */
@Service
@Transactional(readOnly = true)
public class GroupService {

    /** No 0/O, 1/I/L: easy to read out loud or type from a screenshot. 31^6 ≈ 887 million codes. */
    private static final String CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("EEE d MMM, h:mm a", Locale.ENGLISH).withZone(ZoneId.of("Asia/Kolkata"));

    private final GroupRepository groupRepository;
    private final GroupMemberRepository memberRepository;
    private final UserDirectory users;
    private final Map<ItemType, InventoryProvider> inventories = new EnumMap<>(ItemType.class);
    private final String baseUrl;

    public GroupService(GroupRepository groupRepository, GroupMemberRepository memberRepository, UserDirectory users,
                        List<InventoryProvider> providers, @Value("${app.base-url}") String baseUrl) {
        this.groupRepository = groupRepository;
        this.memberRepository = memberRepository;
        this.users = users;
        this.baseUrl = baseUrl;
        providers.forEach(p -> inventories.put(p.type(), p));
    }

    @Transactional
    public GroupCreated create(UUID callerId, long showId) {
        UserCard leader = users.requireActive(callerId);
        ItemSummary show = describe(ItemType.SHOW, showId);
        if (!show.bookable()) {
            throw new ApiException(ErrorCode.SHOW_NOT_BOOKABLE);
        }

        BookingGroup group = new BookingGroup();
        group.setCode(newCode());
        group.setItemType(ItemType.SHOW);
        group.setItemId(showId);
        group.setLeaderId(leader.id());
        groupRepository.save(group);
        addMember(group, leader.id(), MemberRole.LEADER);

        return new GroupCreated(group.getPublicId(), group.getCode(), shareLink(group), whatsappUrl(group, show));
    }

    /** Public: anyone with the code (from WhatsApp) can see what they'd be joining. */
    public GroupPreview preview(String code) {
        BookingGroup group = groupRepository.findByCode(code.toUpperCase(Locale.ROOT))
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        return new GroupPreview(group.getCode(), group.getStatus().name(),
                (int) memberRepository.countByGroupId(group.getId()), group.getMaxMembers(),
                describe(group.getItemType(), group.getItemId()));
    }

    /** Joining twice is fine: you're simply returned the group. */
    @Transactional
    public UUID join(UUID callerId, String code) {
        UserCard user = users.requireActive(callerId);
        BookingGroup group = groupRepository.lockByCode(code.toUpperCase(Locale.ROOT))   // row lock: last place goes once
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        if (memberRepository.findByGroupIdAndUserId(group.getId(), user.id()).isPresent()) {
            return group.getPublicId();
        }
        requireOpen(group);
        if (memberRepository.countByGroupId(group.getId()) >= group.getMaxMembers()) {
            throw new ApiException(ErrorCode.GROUP_FULL);
        }
        addMember(group, user.id(), MemberRole.MEMBER);
        return group.getPublicId();
    }

    public GroupView view(UUID callerId, UUID groupId) {
        UserCard caller = users.requireActive(callerId);
        BookingGroup group = memberGroup(groupId, caller.id());
        List<GroupMember> members = memberRepository.findByGroupIdOrderByJoinedAtAsc(group.getId());
        Map<Long, UserCard> cards = users.byIds(members.stream().map(GroupMember::getUserId).toList());
        ItemSummary item = describe(group.getItemType(), group.getItemId());

        List<MemberView> views = members.stream().map(m -> {
            UserCard card = cards.get(m.getUserId());
            return new MemberView(card.publicId(), card.name(), card.avatarUrl(), m.getRole().name(),
                    m.getStatus().name(), m.getUserId().equals(caller.id()));
        }).toList();
        return new GroupView(group.getPublicId(), group.getCode(), group.getStatus().name(), group.getMaxMembers(),
                group.getLeaderId().equals(caller.id()), item, views, shareLink(group), whatsappUrl(group, item));
    }

    public List<GroupCard> myGroups(UUID callerId) {
        UserCard caller = users.requireActive(callerId);
        return groupRepository.findAllForMember(caller.id()).stream()
                .map(g -> new GroupCard(g.getPublicId(), g.getCode(), g.getStatus().name(),
                        (int) memberRepository.countByGroupId(g.getId()), g.getLeaderId().equals(caller.id()),
                        describe(g.getItemType(), g.getItemId())))
                .toList();
    }

    /** The leader removes someone, or a member leaves (removes themselves). The leader can't leave: cancel instead. */
    @Transactional
    public void removeMember(UUID callerId, UUID groupId, UUID memberPublicId) {
        UserCard caller = users.requireActive(callerId);
        BookingGroup group = memberGroup(groupId, caller.id());
        UserCard target = users.byPublicId(memberPublicId).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        boolean self = target.id() == caller.id();
        boolean callerIsLeader = group.getLeaderId().equals(caller.id());

        if (!self && !callerIsLeader) {
            throw new ApiException(ErrorCode.NOT_LEADER);
        }
        if (target.id() == group.getLeaderId()) {
            throw new ApiException(ErrorCode.LEADER_CANNOT_LEAVE);
        }
        requireOpen(group);
        GroupMember member = memberRepository.findByGroupIdAndUserId(group.getId(), target.id())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        memberRepository.delete(member);
    }

    /** Leader only. Once payments exist (booking module) this also checks nobody has paid yet. */
    @Transactional
    public void cancel(UUID callerId, UUID groupId) {
        UserCard caller = users.requireActive(callerId);
        BookingGroup group = memberGroup(groupId, caller.id());
        if (!group.getLeaderId().equals(caller.id())) {
            throw new ApiException(ErrorCode.NOT_LEADER);
        }
        requireOpen(group);
        group.setStatus(GroupStatus.CANCELLED);
    }

    // ---------------------------------------------------------------- helpers

    /** The group, if the caller is in it. Otherwise 404, so outsiders can't even tell the group exists. */
    private BookingGroup memberGroup(UUID groupId, long userId) {
        BookingGroup group = groupRepository.findByPublicId(groupId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        if (memberRepository.findByGroupIdAndUserId(group.getId(), userId).isEmpty()) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
        return group;
    }

    private static void requireOpen(BookingGroup group) {
        switch (group.getStatus()) {
            case BOOKED -> throw new ApiException(ErrorCode.GROUP_BOOKED);
            case CANCELLED -> throw new ApiException(ErrorCode.GROUP_CANCELLED);
            case OPEN -> { }
        }
    }

    private void addMember(BookingGroup group, long userId, MemberRole role) {
        GroupMember member = new GroupMember();
        member.setGroupId(group.getId());
        member.setUserId(userId);
        member.setRole(role);
        memberRepository.save(member);
    }

    private ItemSummary describe(ItemType type, long itemId) {
        InventoryProvider provider = inventories.get(type);
        if (provider == null) {
            throw new IllegalStateException("No InventoryProvider for " + type);
        }
        return provider.describe(itemId).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
    }

    private String newCode() {
        for (int attempt = 0; attempt < 10; attempt++) {
            StringBuilder code = new StringBuilder(6);
            for (int i = 0; i < 6; i++) {
                code.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
            }
            if (!groupRepository.existsByCode(code.toString())) {
                return code.toString();
            }
        }
        throw new IllegalStateException("Could not find a free group code");   // ~impossible with 887M codes
    }

    private String shareLink(BookingGroup group) {
        return baseUrl + "/g/" + group.getCode();
    }

    private String whatsappUrl(BookingGroup group, ItemSummary item) {
        String text = "Let's watch " + item.title() + " together! " + WHEN.format(item.startsAt()) + " at "
                + item.venue() + ". Join my Ticktit group and pick your seat: " + shareLink(group);
        return "https://wa.me/?text=" + URLEncoder.encode(text, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
