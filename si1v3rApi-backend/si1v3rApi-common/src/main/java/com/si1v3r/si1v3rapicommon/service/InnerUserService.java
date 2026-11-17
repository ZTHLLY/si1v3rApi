package com.si1v3r.si1v3rapicommon.service;

import com.si1v3r.si1v3rapicommon.model.InvokeUser;

public interface InnerUserService {
    /** 按 assessKey 查用户；不存在或已逻辑删除返回 null。是否 ban 由网关判断。 */
    InvokeUser getInvokeUser(String assessKey);
}
