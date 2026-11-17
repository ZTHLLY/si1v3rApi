package com.si1v3r.si1v3rapicommon.service;

public interface InnerUserInterfaceInfoService {
    /**
     * v1.2：调用成功后记一次调用（只统计，不限额）。必须是一条原子 SQL：
     * INSERT INTO user_interface_info (userId, interfaceInfoId, totalNum) VALUES (?, ?, 1)
     * ON DUPLICATE KEY UPDATE totalNum = totalNum + 1, isDelete = 0
     * 依赖唯一索引 uk_user_interface；第一次调用自动建记录。执行成功返回 true（参数非法返回 false）。
     */
    boolean invokeCount(long interfaceInfoId, long userId);
}
