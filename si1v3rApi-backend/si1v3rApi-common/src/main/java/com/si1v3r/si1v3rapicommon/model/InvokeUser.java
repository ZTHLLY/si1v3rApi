package com.si1v3r.si1v3rapicommon.model;

import java.io.Serializable;

/** Minimal RPC DTO; deliberately excludes persistence and password fields. */
public class InvokeUser implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long id;
    private String assessKey;
    private String secretKey;
    private String userRole;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getAssessKey() { return assessKey; }
    public void setAssessKey(String assessKey) { this.assessKey = assessKey; }

    public String getSecretKey() { return secretKey; }
    public void setSecretKey(String secretKey) { this.secretKey = secretKey; }

    public String getUserRole() { return userRole; }
    public void setUserRole(String userRole) { this.userRole = userRole; }
}
