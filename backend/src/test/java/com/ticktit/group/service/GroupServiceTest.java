package com.ticktit.group.service;

import com.ticktit.auth.entity.User;
import com.ticktit.auth.entity.UserStatus;
import com.ticktit.auth.repository.UserRepository;
import com.ticktit.common.enums.ErrorCode;
import com.ticktit.common.exception.ApiException;
import com.ticktit.group.dto.GroupDtos.GroupCreated;
import com.ticktit.group.dto.GroupDtos.GroupView;
import com.ticktit.movie.service.CatalogService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Real database, demo catalogue show. Test users are named group-test-*@example.com and removed afterwards. */
@SpringBootTest
class GroupServiceTest {

    @Autowired GroupService groups;
    @Autowired CatalogService catalog;
    @Autowired UserRepository userRepository;
    @Autowired JdbcTemplate jdbc;

    UUID asha, rahul, sneha;
    long showId;

    @BeforeEach
    void setUp() {
        asha = user("asha");
        rahul = user("rahul");
        sneha = user("sneha");
        showId = catalog.shows("paper-kites", "siliguri", LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(1))
                .getFirst().id();
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM groups WHERE leader_id IN (SELECT id FROM users WHERE email LIKE 'group-test-%')");
        jdbc.update("DELETE FROM users WHERE email LIKE 'group-test-%'");
    }

    @Test
    void leaderCreatesFriendsJoinEveryoneSeesTheMembers() {
        GroupCreated created = groups.create(asha, showId);
        assertEquals(6, created.code().length());
        assertTrue(created.shareLink().endsWith("/g/" + created.code()));
        assertTrue(created.whatsappShareUrl().startsWith("https://wa.me/?text="));

        assertEquals(created.groupId(), groups.join(rahul, created.code().toLowerCase()));   // code is case-insensitive
        GroupView view = groups.view(rahul, created.groupId());

        assertEquals(2, view.members().size());
        assertFalse(view.youAreLeader());
        assertEquals("LEADER", view.members().getFirst().role());
        assertTrue(view.members().get(1).you());
    }

    @Test
    void previewIsPublicAndHasNoNames() {
        GroupCreated created = groups.create(asha, showId);
        var preview = groups.preview(created.code());

        assertEquals(1, preview.memberCount());
        assertEquals("Paper Kites", preview.item().title());
        assertFalse(preview.toString().contains("group-test"));   // no emails or names leak
    }

    @Test
    void joiningTwiceIsHarmless() {
        GroupCreated created = groups.create(asha, showId);
        groups.join(rahul, created.code());
        groups.join(rahul, created.code());
        assertEquals(2, groups.view(asha, created.groupId()).members().size());
    }

    @Test
    void fullGroupRefusesNewMembers() {
        GroupCreated created = groups.create(asha, showId);
        jdbc.update("UPDATE groups SET max_members = 2 WHERE code = ?", created.code());
        groups.join(rahul, created.code());

        ApiException ex = assertThrows(ApiException.class, () -> groups.join(sneha, created.code()));
        assertEquals(ErrorCode.GROUP_FULL, ex.getErrorCode());
    }

    @Test
    void outsidersCannotSeeAGroup() {
        GroupCreated created = groups.create(asha, showId);
        ApiException ex = assertThrows(ApiException.class, () -> groups.view(sneha, created.groupId()));
        assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void onlyTheLeaderRemovesOthersButAnyoneCanLeave() {
        GroupCreated created = groups.create(asha, showId);
        groups.join(rahul, created.code());
        groups.join(sneha, created.code());

        ApiException notLeader = assertThrows(ApiException.class, () -> groups.removeMember(rahul, created.groupId(), sneha));
        assertEquals(ErrorCode.NOT_LEADER, notLeader.getErrorCode());

        groups.removeMember(rahul, created.groupId(), rahul);    // Rahul leaves
        groups.removeMember(asha, created.groupId(), sneha);     // Asha removes Sneha
        assertEquals(1, groups.view(asha, created.groupId()).members().size());

        ApiException leaderLeaves = assertThrows(ApiException.class, () -> groups.removeMember(asha, created.groupId(), asha));
        assertEquals(ErrorCode.LEADER_CANNOT_LEAVE, leaderLeaves.getErrorCode());
    }

    @Test
    void cancelledGroupCannotBeJoined() {
        GroupCreated created = groups.create(asha, showId);
        assertThrows(ApiException.class, () -> groups.cancel(rahul, created.groupId()));   // not even a member
        groups.cancel(asha, created.groupId());

        ApiException ex = assertThrows(ApiException.class, () -> groups.join(rahul, created.code()));
        assertEquals(ErrorCode.GROUP_CANCELLED, ex.getErrorCode());
    }

    @Test
    void showThatAlreadyStartedCannotGetAGroup() {
        jdbc.update("UPDATE shows SET booking_opens_at = now() + interval '1 day' WHERE id = ?", showId);
        try {
            ApiException ex = assertThrows(ApiException.class, () -> groups.create(asha, showId));
            assertEquals(ErrorCode.SHOW_NOT_BOOKABLE, ex.getErrorCode());
        } finally {
            jdbc.update("UPDATE shows SET booking_opens_at = starts_at - interval '14 days' WHERE id = ?", showId);
        }
    }

    private UUID user(String name) {
        User u = new User();
        u.setName(name.substring(0, 1).toUpperCase() + name.substring(1) + " Test");
        u.setEmail("group-test-" + name + "@example.com");
        u.setPasswordHash("hash");
        u.setStatus(UserStatus.ACTIVE);
        u.setEmailVerifiedAt(Instant.now());
        return userRepository.save(u).getPublicId();
    }
}
