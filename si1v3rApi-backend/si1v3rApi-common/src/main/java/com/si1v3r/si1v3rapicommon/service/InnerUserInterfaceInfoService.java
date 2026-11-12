package com.si1v3r.si1v3rapicommon.service;

public interface InnerUserInterfaceInfoService {
    /** 该用户对该接口是否还能调用：存在关系记录、status=0、leftNum>0。 */
    boolean hasLeftNum(long interfaceInfoId, long userId);

    /**
     * 调用成功后扣减一次。必须是一条原子 SQL：
     * UPDATE user_interface_info SET leftNum = leftNum - 1, totalNum = totalNum + 1
     * WHERE interfaceInfoId = ? AND userId = ? AND status = 0 AND leftNum > 0 AND isDelete = 0
     * 影响行数为 1 返回 true，否则 false。
     */
    boolean invokeCount(long interfaceInfoId, long userId);
}
