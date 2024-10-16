package com.si1v3r.si1v3rApi.service;

import com.baomidou.mybatisplus.extension.service.IService;

import com.si1v3r.si1v3rApi.model.entity.UserInterfaceInfo;

/**
* @author silverbullets
* @description 针对表【user_interface_info(用户调用接口关系)】的数据库操作Service
* @createDate 2024-10-15 17:28:14
*/
public interface UserInterfaceInfoService extends IService<UserInterfaceInfo> {
    void validUserInterfaceInfo(UserInterfaceInfo userinterfaceInfo, boolean add);

    /**
     * 接口调用统计
     * @param interfaceInfoId
     * @param userId
     * @return
     */
    boolean invokeCount(long interfaceInfoId,long userId);

}
