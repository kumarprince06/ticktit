package com.ticktit.group.controller;

import com.ticktit.group.dto.GroupDtos.CreateGroupRequest;
import com.ticktit.group.dto.GroupDtos.GroupCard;
import com.ticktit.group.dto.GroupDtos.GroupCreated;
import com.ticktit.group.dto.GroupDtos.GroupPreview;
import com.ticktit.group.dto.GroupDtos.GroupView;
import com.ticktit.group.service.GroupService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Everything needs sign-in except the preview (SecurityConfig permits GET /groups/preview/**). */
@RestController
@RequestMapping("/api/v1/groups")
@RequiredArgsConstructor
public class GroupController {

    private final GroupService groupService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GroupCreated create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateGroupRequest request) {
        return groupService.create(caller(jwt), request.showId());
    }

    @GetMapping("/preview/{code}")
    public GroupPreview preview(@PathVariable String code) {
        return groupService.preview(code);
    }

    @PostMapping("/{code}/join")
    public Map<String, UUID> join(@AuthenticationPrincipal Jwt jwt, @PathVariable String code) {
        return Map.of("groupId", groupService.join(caller(jwt), code));
    }

    @GetMapping("/my")
    public List<GroupCard> myGroups(@AuthenticationPrincipal Jwt jwt) {
        return groupService.myGroups(caller(jwt));
    }

    @GetMapping("/{id}")
    public GroupView view(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return groupService.view(caller(jwt), id);
    }

    /** Leader removes a member, or a member removes themselves (leaves). */
    @DeleteMapping("/{id}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @PathVariable UUID userId) {
        groupService.removeMember(caller(jwt), id, userId);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        groupService.cancel(caller(jwt), id);
    }

    private static UUID caller(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
