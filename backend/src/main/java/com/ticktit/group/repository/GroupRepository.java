package com.ticktit.group.repository;

import com.ticktit.group.entity.BookingGroup;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GroupRepository extends JpaRepository<BookingGroup, Long> {

    Optional<BookingGroup> findByCode(String code);

    Optional<BookingGroup> findByPublicId(UUID publicId);

    /** Join locks the group row so two people can't both take the last place. Caller must be @Transactional. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT g FROM BookingGroup g WHERE g.code = :code")
    Optional<BookingGroup> lockByCode(String code);

    boolean existsByCode(String code);

    @Query("""
            SELECT g FROM BookingGroup g WHERE g.id IN (SELECT m.groupId FROM GroupMember m WHERE m.userId = :userId)
            ORDER BY g.createdAt DESC""")
    List<BookingGroup> findAllForMember(long userId);
}
