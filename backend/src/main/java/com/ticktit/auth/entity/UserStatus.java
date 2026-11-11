package com.ticktit.auth.entity;

public enum UserStatus {
    PENDING_VERIFICATION,
    ACTIVE,
    BLOCKED,
    DELETED          // never rename: the DB check ck_user_login_method uses 'DELETED'
}
