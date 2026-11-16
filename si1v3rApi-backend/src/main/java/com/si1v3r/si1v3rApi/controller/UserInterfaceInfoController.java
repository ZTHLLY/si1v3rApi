package com.si1v3r.si1v3rApi.controller;
import com.si1v3r.si1v3rApi.annotation.AuthCheck;
import com.si1v3r.si1v3rApi.common.*;
import com.si1v3r.si1v3rApi.constant.UserConstant;
import com.si1v3r.si1v3rApi.exception.BusinessException;
import com.si1v3r.si1v3rApi.exception.ThrowUtils;

import com.si1v3r.si1v3rApi.model.entity.User;
import com.si1v3r.si1v3rApi.model.entity.UserInterfaceInfo;
import com.si1v3r.si1v3rApi.model.vo.MyInvokeStatVO;
import java.util.List;

import com.si1v3r.si1v3rApi.service.UserInterfaceInfoService;
import com.si1v3r.si1v3rApi.service.UserService;
import org.springframework.web.bind.annotation.*;
import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

/**
 * 用户调用统计接口
 *
 * @author si1v3r
 */
@RestController
@RequestMapping("/userInterfaceInfo")
public class UserInterfaceInfoController {

    @Resource
    private UserInterfaceInfoService userInterfaceInfoService;

    @Resource
    private UserService userService;


    @PostMapping("/delete")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> deleteUserInterfaceInfo(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        long id = deleteRequest.getId();
        // 判断是否存在
        UserInterfaceInfo oldUserInterfaceInfo = userInterfaceInfoService.getById(id);
        ThrowUtils.throwIf(oldUserInterfaceInfo == null, ErrorCode.NOT_FOUND_ERROR);
        boolean b = userInterfaceInfoService.removeById(id);
        return ResultUtils.success(b);
    }

    @GetMapping("/get")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<UserInterfaceInfo> getUserInterfaceInfoVOById(long id) {
        if (id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        UserInterfaceInfo userInterfaceInfo = userInterfaceInfoService.getById(id);
        if (userInterfaceInfo == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        return ResultUtils.success(userInterfaceInfo);
    }

    @GetMapping("/my/list")
    public BaseResponse<List<MyInvokeStatVO>> listMyInvokeStats(HttpServletRequest request) {
        User user = userService.getLoginUser(request);
        return ResultUtils.success(userInterfaceInfoService.listMyInvokeStats(user.getId()));
    }
}
