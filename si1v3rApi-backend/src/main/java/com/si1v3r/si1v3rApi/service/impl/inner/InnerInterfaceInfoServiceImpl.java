package com.si1v3r.si1v3rApi.service.impl.inner;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.si1v3r.si1v3rapicommon.service.InnerInterfaceInfoService;
import com.si1v3r.si1v3rapicommon.model.InvokeInterface;
import com.si1v3r.si1v3rApi.model.entity.InterfaceInfo;
import com.si1v3r.si1v3rApi.service.InterfaceInfoService;
import org.apache.dubbo.config.annotation.DubboService;
import javax.annotation.Resource;
import java.util.Comparator;
import java.util.Locale;

@DubboService
public class InnerInterfaceInfoServiceImpl implements InnerInterfaceInfoService {
    @Resource
    private InterfaceInfoService interfaceInfoService;

    @Override
    public InvokeInterface getInterfaceInfo(String url, String method) {
        if (url == null || url.isEmpty() || method == null) { return null; }
        InterfaceInfo info = interfaceInfoService.list(new QueryWrapper<InterfaceInfo>()
                .select("id", "url", "method", "status")
                .eq("url", url))
                .stream()
                // SQL uses the URL index; Java enforces exact matching despite the DB collation.
                .filter(candidate -> url.equals(candidate.getUrl())
                        && method.equalsIgnoreCase(candidate.getMethod()))
                .min(Comparator.comparing(InterfaceInfo::getId))
                .orElse(null);
        if (info == null) { return null; }
        InvokeInterface result = new InvokeInterface();
        result.setId(info.getId());
        result.setUrl(info.getUrl());
        result.setMethod(info.getMethod().toUpperCase(Locale.ROOT));
        result.setStatus(info.getStatus());
        return result;
    }
}
