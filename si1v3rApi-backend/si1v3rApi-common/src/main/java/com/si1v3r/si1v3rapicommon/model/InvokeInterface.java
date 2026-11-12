package com.si1v3r.si1v3rapicommon.model;

import java.io.Serializable;

/** Minimal RPC DTO; deliberately excludes persistence and password fields. */
public class InvokeInterface implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long id;
    private String url;
    private String method;
    private Integer status;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
}
