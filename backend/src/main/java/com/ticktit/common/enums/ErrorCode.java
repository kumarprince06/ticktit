package com.ticktit.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    //Generic
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Please check the highlighted fields and try again"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Please sign in to continue"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "You don't have permission to do this"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "We couldn't find what you were looking for"),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "The request could not be read. Please check it and try again"),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "This action is not supported here"),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong on our side. Please try again in a moment"),
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "Too many attempts. Please wait a few minutes and try again"),

    // Auth
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Incorrect email or password"),
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "An account with this email already exists. Please sign in instead"),
    INVALID_GOOGLE_TOKEN(HttpStatus.UNAUTHORIZED, "Google sign-in failed. Please try again"),
    INVALID_PASSWORD_RESET_TOKEN(HttpStatus.BAD_REQUEST, "This password reset link is invalid. Please request a new one"),
    PASSWORD_RESET_TOKEN_EXPIRED(HttpStatus.BAD_REQUEST, "This password reset link has expired. Please request a new one"),
    EMAIL_NOT_VERIFIED(HttpStatus.FORBIDDEN, "Please verify your email first. Check your inbox for the verification link"),
    // Never use in login/signup/forgot-password: it would reveal which emails are registered
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "User not found"),
    LINK_INVALID(HttpStatus.BAD_REQUEST, "This link is invalid or has expired. Please request a new one"),
    OTP_INVALID(HttpStatus.BAD_REQUEST, "The code is incorrect or has expired. Please try again or request a new code"),
    REFRESH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "Your session has expired. Please sign in again"),

    // Groups
    SHOW_NOT_BOOKABLE(HttpStatus.CONFLICT, "This show can't be booked any more. Please pick another show"),
    GROUP_FULL(HttpStatus.BAD_REQUEST, "This group is full. Ask the organiser to start a new one"),
    GROUP_BOOKED(HttpStatus.CONFLICT, "This group has already booked its tickets"),
    GROUP_CANCELLED(HttpStatus.CONFLICT, "The organiser cancelled this group"),
    NOT_LEADER(HttpStatus.FORBIDDEN, "Only the group organiser can do this"),
    LEADER_CANNOT_LEAVE(HttpStatus.CONFLICT, "You organise this group. Cancel the group instead of leaving it");

    private final HttpStatus httpStatus;
    private final String message;

}
