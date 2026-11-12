package com.si1v3r.si1v3rApi.service.impl.inner;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.si1v3r.si1v3rapicommon.service.InnerUserInterfaceInfoService;
import com.si1v3r.si1v3rApi.model.entity.UserInterfaceInfo;
import com.si1v3r.si1v3rApi.service.UserInterfaceInfoService;
import org.apache.dubbo.config.annotation.DubboService;
import javax.annotation.Resource;

@DubboService
public class InnerUserInterfaceInfoServiceImpl implements InnerUserInterfaceInfoService {
    @Resource
    private UserInterfaceInfoService userInterfaceInfoService;

    @Override
    public boolean hasLeftNum(long interfaceInfoId, long userId) {
        return userInterfaceInfoService.count(new QueryWrapper<UserInterfaceInfo>()
                .eq("interfaceInfoId", interfaceInfoId).eq("userId", userId)
                .eq("status", 0).gt("leftNum", 0).eq("isDelete", 0)) > 0;
    }

    @Override
    public boolean invokeCount(long interfaceInfoId, long userId) {
        return userInterfaceInfoService.invokeCount(interfaceInfoId, userId);
    }
}
