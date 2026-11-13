package com.ticktit.group.repository;

import com.ticktit.group.entity.GroupMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GroupMemberRepository extends JpaRepository<GroupMember, Long> {

    List<GroupMember> findByGroupIdOrderByJoinedAtAsc(long groupId);

    Optional<GroupMember> findByGroupIdAndUserId(long groupId, long userId);

    long countByGroupId(long groupId);
}
