package com.ticktit.payment.gateway;

/** The gateway refused or couldn't be reached. Callers decide whether it's a user error or a retry. */
public class GatewayException extends RuntimeException {

    private final int status;

    public GatewayException(String message, int status, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    /** HTTP status from the gateway, or 0 when it couldn't be reached. */
    public int status() {
        return status;
    }
}
