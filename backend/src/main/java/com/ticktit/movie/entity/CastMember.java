package com.ticktit.movie.entity;

/** One entry of movies.cast_members (JSONB). role: ACTOR / DIRECTOR / MUSIC. */
public record CastMember(String name, String role, String character) {
}
