package com.ticktit.auth.controller;

import com.ticktit.auth.dto.AuthDtos.UserSummary;
import com.ticktit.auth.entity.UserStatus;
import com.ticktit.auth.repository.UserRepository;
import com.ticktit.common.enums.ErrorCode;
import com.ticktit.common.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** The signed-in user, e.g. for the app to show the name after a page reload. Needs a valid access token. */
@RestController
@RequiredArgsConstructor
public class MeController {

    private final UserRepository userRepository;

    @GetMapping("/api/v1/me")
    public UserSummary me(@AuthenticationPrincipal Jwt jwt) {
        return userRepository.findByPublicId(UUID.fromString(jwt.getSubject()))
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .map(user -> new UserSummary(user.getPublicId(), user.getName(), user.getEmail(), user.getRole().name(),
                        user.getAvatarUrl()))
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));   // deleted/blocked since the token was issued
    }
}
