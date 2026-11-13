package com.ticktit.auth.service;

import com.ticktit.auth.entity.User;
import com.ticktit.auth.entity.UserStatus;
import com.ticktit.auth.repository.UserRepository;
import com.ticktit.common.enums.ErrorCode;
import com.ticktit.common.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The auth module's public face for other modules: who is this user, what's their name.
 * Other modules use this instead of the users table, so auth stays the only owner of user data.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserDirectory {

    private final UserRepository userRepository;

    /** Public view of a user. firstName is what other members of a group see. */
    public record UserCard(long id, UUID publicId, String name, String avatarUrl) {
        public String firstName() {
            return name.trim().split("\\s+")[0];
        }
    }

    /** The caller of a request, from the JWT "sub". 401 if the account is gone or blocked since sign-in. */
    public UserCard requireActive(UUID publicId) {
        return userRepository.findByPublicId(publicId)
                .filter(u -> u.getStatus() == UserStatus.ACTIVE)
                .map(UserDirectory::card)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));
    }

    public Map<Long, UserCard> byIds(Collection<Long> ids) {
        return userRepository.findAllById(ids).stream()
                .map(UserDirectory::card)
                .collect(Collectors.toMap(UserCard::id, Function.identity()));
    }

    /** Email + phone, for payment prefill and the booking's contact details. */
    public record Contact(String name, String email, String phone) {
    }

    public Contact contact(long userId) {
        User u = userRepository.findById(userId).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        return new Contact(u.getName(), u.getEmail(), u.getPhone());
    }

    public java.util.Optional<UserCard> byPublicId(UUID publicId) {
        return userRepository.findByPublicId(publicId).map(UserDirectory::card);
    }

    private static UserCard card(User u) {
        return new UserCard(u.getId(), u.getPublicId(), u.getName(), u.getAvatarUrl());
    }
}
