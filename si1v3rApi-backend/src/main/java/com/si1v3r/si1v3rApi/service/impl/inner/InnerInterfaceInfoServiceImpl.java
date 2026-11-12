package com.si1v3r.si1v3rApi.service.impl.inner;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.si1v3r.si1v3rapicommon.service.InnerInterfaceInfoService;
import com.si1v3r.si1v3rapicommon.model.InvokeInterface;
import com.si1v3r.si1v3rApi.model.entity.InterfaceInfo;
import com.si1v3r.si1v3rApi.service.InterfaceInfoService;
import org.apache.dubbo.config.annotation.DubboService;
import javax.annotation.Resource;
import java.util.Locale;

@DubboService
public class InnerInterfaceInfoServiceImpl implements InnerInterfaceInfoService {
    @Resource
    private InterfaceInfoService interfaceInfoService;

    @Override
    public InvokeInterface getInterfaceInfo(String url, String method) {
        if (url == null || method == null) { return null; }
        InterfaceInfo info = interfaceInfoService.getOne(new QueryWrapper<InterfaceInfo>()
                .select("id", "url", "method", "status")
                .apply("BINARY url = {0}", url)
                .apply("UPPER(method) = {0}", method.toUpperCase(Locale.ROOT))
                .eq("isDelete", 0));
        if (info == null) { return null; }
        InvokeInterface result = new InvokeInterface();
        result.setId(info.getId());
        result.setUrl(info.getUrl());
        result.setMethod(info.getMethod().toUpperCase(Locale.ROOT));
        result.setStatus(info.getStatus());
        return result;
    }
}
