package com.si1v3r.si1v3rApi.model.vo;

import java.io.Serializable;
import java.util.Date;
import lombok.Data;

/** Administrator view: credentials must never be serialized. */
@Data
public class AdminUserVO implements Serializable {
    private Long id;
    private String userAccount;
    private String unionId;
    private String mpOpenId;
    private String userName;
    private String userAvatar;
    private String userProfile;
    private String userRole;
    private String assessKey;
    private Date createTime;
    private Date updateTime;
    private Integer isDelete;
    private static final long serialVersionUID = 1L;
}
