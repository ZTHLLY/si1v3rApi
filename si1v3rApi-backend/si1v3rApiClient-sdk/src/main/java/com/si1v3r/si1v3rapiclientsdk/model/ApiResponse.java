package com.si1v3r.si1v3rapiclientsdk.model;

/** HTTP status and UTF-8 response body, including non-2xx responses. */
public final class ApiResponse {
    private final int status;
    private final String body;

    public ApiResponse(int status, String body) {
        this.status = status;
        this.body = body;
    }

    public int getStatus() { return status; }
    public String getBody() { return body; }
}
