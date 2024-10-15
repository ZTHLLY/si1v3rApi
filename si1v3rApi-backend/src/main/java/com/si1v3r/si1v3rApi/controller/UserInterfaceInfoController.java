package com.si1v3r.si1v3rApi.controller;

import com.si1v3r.si1v3rApi.annotation.AuthCheck;
import com.si1v3r.si1v3rApi.common.*;
import com.si1v3r.si1v3rApi.constant.UserConstant;
import com.si1v3r.si1v3rApi.exception.BusinessException;
import com.si1v3r.si1v3rApi.exception.ThrowUtils;

import com.si1v3r.si1v3rApi.model.dto.userinterfaceinfo.UserInterfaceInfoAddRequest;

import com.si1v3r.si1v3rApi.model.dto.userinterfaceinfo.UserInterfaceInfoUpdateRequest;
import com.si1v3r.si1v3rApi.model.entity.User;
import com.si1v3r.si1v3rApi.model.entity.UserInterfaceInfo;


import com.si1v3r.si1v3rApi.service.UserInterfaceInfoService;
import com.si1v3r.si1v3rApi.service.UserService;
import com.si1v3r.si1v3rapiclientsdk.client.Si1v3rApiClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

/**
 * 帖子接口
 *
 * @author si1v3r
 */
@RestController
@RequestMapping("/userUserInterfaceInfo")
@Slf4j
public class UserInterfaceInfoController {

    @Resource
    private UserInterfaceInfoService userInterfaceInfoService;

    @Resource
    private UserService userService;

    @Resource
    private Si1v3rApiClient si1v3rApiClient;

    // region 增删改查

    /**
     * 创建
     *
     * @param userUserInterfaceInfoAddRequest
     * @param request
     * @return
     */
    @PostMapping("/add")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Long> addUserInterfaceInfo(@RequestBody UserInterfaceInfoAddRequest userUserInterfaceInfoAddRequest, HttpServletRequest request) {
        if (userUserInterfaceInfoAddRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        //新建要添加的对象
        UserInterfaceInfo userUserInterfaceInfo = new UserInterfaceInfo();
        BeanUtils.copyProperties(userUserInterfaceInfoAddRequest, userUserInterfaceInfo);

        userInterfaceInfoService.validUserInterfaceInfo(userUserInterfaceInfo, true);
        User loginUser = userService.getLoginUser(request);
        userUserInterfaceInfo.setUserId(loginUser.getId());

        boolean result = userInterfaceInfoService.save(userUserInterfaceInfo);

        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        long newUserInterfaceInfoId = userUserInterfaceInfo.getId();
        return ResultUtils.success(newUserInterfaceInfoId);
    }

    /**
     * 删除
     *
     * @param deleteRequest
     * @param request
     * @return
     */
    @PostMapping("/delete")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> deleteUserInterfaceInfo(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User user = userService.getLoginUser(request);
        long id = deleteRequest.getId();
        // 判断是否存在
        UserInterfaceInfo oldUserInterfaceInfo = userInterfaceInfoService.getById(id);
        ThrowUtils.throwIf(oldUserInterfaceInfo == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人或管理员可删除
        if (!oldUserInterfaceInfo.getUserId().equals(user.getId()) && !userService.isAdmin(request)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        boolean b = userInterfaceInfoService.removeById(id);
        return ResultUtils.success(b);
    }

    /**
     * 更新（仅管理员）
     *
     * @param userUserInterfaceInfoUpdateRequest
     * @return
     */
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateUserInterfaceInfo(@RequestBody UserInterfaceInfoUpdateRequest userUserInterfaceInfoUpdateRequest) {
        if (userUserInterfaceInfoUpdateRequest == null || userUserInterfaceInfoUpdateRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        UserInterfaceInfo userUserInterfaceInfo = new UserInterfaceInfo();
        BeanUtils.copyProperties(userUserInterfaceInfoUpdateRequest, userUserInterfaceInfo);

        // 参数校验
        userInterfaceInfoService.validUserInterfaceInfo(userUserInterfaceInfo, false);
        long id = userUserInterfaceInfoUpdateRequest.getId();
        // 判断是否存在
        UserInterfaceInfo oldUserInterfaceInfo = userInterfaceInfoService.getById(id);
        ThrowUtils.throwIf(oldUserInterfaceInfo == null, ErrorCode.NOT_FOUND_ERROR);
        boolean result = userInterfaceInfoService.updateById(userUserInterfaceInfo);
        return ResultUtils.success(result);
    }

    /**
     * 根据 id 获取
     *
     * @param id
     * @return
     */
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

//    /**
//     * 分页获取列表（仅管理员）
//     *
//     * @param userUserInterfaceInfoQueryRequest
//     * @return
//     */
//    @PostMapping("/list/page")
//    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
//    public BaseResponse<Page<UserInterfaceInfo>> listUserInterfaceInfoByPage(@RequestBody UserInterfaceInfoQueryRequest userUserInterfaceInfoQueryRequest) {
//        long current = userUserInterfaceInfoQueryRequest.getCurrent();
//        long size = userUserInterfaceInfoQueryRequest.getPageSize();
//
//        Page<UserInterfaceInfo> userInterfaceInfoService = userInterfaceInfoService.page(new Page<>(current, size),
//                userInterfaceInfoService.getQueryWrapper(userInterfaceInfoQueryRequest));
//        return ResultUtils.success(userInterfaceInfoService);
//    }






}
