package com.si1v3r.si1v3rapicommon.service;

import com.si1v3r.si1v3rapicommon.model.InvokeInterface;

public interface InnerInterfaceInfoService {
    /** 按网关路径 + 方法（大写）精确匹配未删除的接口；不存在返回 null。是否上线由网关判断。 */
    InvokeInterface getInterfaceInfo(String url, String method);
}
