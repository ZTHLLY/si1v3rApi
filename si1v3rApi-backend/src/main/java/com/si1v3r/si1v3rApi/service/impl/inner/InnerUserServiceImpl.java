package com.si1v3r.si1v3rApi.service.impl.inner;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.si1v3r.si1v3rapicommon.service.InnerUserService;
import com.si1v3r.si1v3rapicommon.model.InvokeUser;
import com.si1v3r.si1v3rApi.model.entity.User;
import com.si1v3r.si1v3rApi.service.UserService;
import org.apache.dubbo.config.annotation.DubboService;
import javax.annotation.Resource;

@DubboService
public class InnerUserServiceImpl implements InnerUserService {
    @Resource
    private UserService userService;

    @Override
    public InvokeUser getInvokeUser(String assessKey) {
        if (assessKey == null || assessKey.isEmpty()) { return null; }
        User user = userService.getOne(new QueryWrapper<User>()
                .select("id", "assessKey", "secretKey", "userRole")
                .eq("assessKey", assessKey).eq("isDelete", 0));
        if (user == null) { return null; }
        InvokeUser result = new InvokeUser();
        result.setId(user.getId());
        result.setAssessKey(user.getAssessKey());
        result.setSecretKey(user.getSecretKey());
        result.setUserRole(user.getUserRole());
        return result;
    }
}
