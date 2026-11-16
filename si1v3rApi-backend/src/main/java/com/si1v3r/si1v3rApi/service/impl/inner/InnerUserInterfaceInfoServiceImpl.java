package com.si1v3r.si1v3rApi.service.impl.inner;

import com.si1v3r.si1v3rapicommon.service.InnerUserInterfaceInfoService;
import com.si1v3r.si1v3rApi.service.UserInterfaceInfoService;
import org.apache.dubbo.config.annotation.DubboService;
import javax.annotation.Resource;

@DubboService
public class InnerUserInterfaceInfoServiceImpl implements InnerUserInterfaceInfoService {
    @Resource
    private UserInterfaceInfoService userInterfaceInfoService;

    @Override
    public boolean invokeCount(long interfaceInfoId, long userId) {
        return userInterfaceInfoService.invokeCount(interfaceInfoId, userId);
    }
}
