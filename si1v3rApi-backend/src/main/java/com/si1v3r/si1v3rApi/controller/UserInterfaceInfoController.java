//package com.si1v3r.si1v3rApi.controller;
//
//import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
//import com.google.gson.Gson;
//import com.si1v3r.si1v3rApi.annotation.AuthCheck;
//import com.si1v3r.si1v3rApi.common.*;
//import com.si1v3r.si1v3rApi.constant.UserConstant;
//import com.si1v3r.si1v3rApi.exception.BusinessException;
//import com.si1v3r.si1v3rApi.exception.ThrowUtils;
//import com.si1v3r.si1v3rApi.model.dto.interfaceinfo.UserInterfaceInfoAddRequest;
//import com.si1v3r.si1v3rApi.model.dto.interfaceinfo.UserInterfaceInfoInvokeRequest;
//import com.si1v3r.si1v3rApi.model.dto.interfaceinfo.UserInterfaceInfoQueryRequest;
//import com.si1v3r.si1v3rApi.model.dto.interfaceinfo.UserInterfaceInfoUpdateRequest;
//import com.si1v3r.si1v3rApi.model.entity.UserInterfaceInfo;
//import com.si1v3r.si1v3rApi.model.entity.User;
//import com.si1v3r.si1v3rApi.model.enums.InterfaceStatusEnum;
//import com.si1v3r.si1v3rApi.model.vo.UserInterfaceInfoVO;
//import com.si1v3r.si1v3rApi.service.UserInterfaceInfoService;
//import com.si1v3r.si1v3rApi.service.UserService;
//import com.si1v3r.si1v3rapiclientsdk.client.Si1v3rApiClient;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.beans.BeanUtils;
//import org.springframework.web.bind.annotation.*;
//
//import javax.annotation.Resource;
//import javax.servlet.http.HttpServletRequest;
//
///**
// * 帖子接口
// *
// * @author si1v3r
// */
//@RestController
//@RequestMapping("/userUserInterfaceInfo")
//@Slf4j
//public class UserUserInterfaceInfoController {
//
//    @Resource
//    private UserInterfaceInfoService userUserInterfaceInfoService;
//
//    @Resource
//    private UserService userService;
//
//    @Resource
//    private Si1v3rApiClient si1v3rApiClient;
//
//    // region 增删改查
//
//    /**
//     * 创建
//     *
//     * @param userUserInterfaceInfoAddRequest
//     * @param request
//     * @return
//     */
//    @PostMapping("/add")
//    public BaseResponse<Long> addUserInterfaceInfo(@RequestBody UserInterfaceInfoAddRequest userUserInterfaceInfoAddRequest, HttpServletRequest request) {
//        if (userUserInterfaceInfoAddRequest == null) {
//            throw new BusinessException(ErrorCode.PARAMS_ERROR);
//        }
//        //新建要添加的对象
//        UserInterfaceInfo userUserInterfaceInfo = new UserInterfaceInfo();
//        BeanUtils.copyProperties(userUserInterfaceInfoAddRequest, userUserInterfaceInfo);
//
//        userUserInterfaceInfoService.validUserInterfaceInfo(userUserInterfaceInfo, true);
//        User loginUser = userService.getLoginUser(request);
//        userUserInterfaceInfo.setUserId(loginUser.getId());
//
//        boolean result = userUserInterfaceInfoService.save(userUserInterfaceInfo);
//
//        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
//        long newUserInterfaceInfoId = userUserInterfaceInfo.getId();
//        return ResultUtils.success(newUserInterfaceInfoId);
//    }
//
//    /**
//     * 删除
//     *
//     * @param deleteRequest
//     * @param request
//     * @return
//     */
//    @PostMapping("/delete")
//    public BaseResponse<Boolean> deleteUserInterfaceInfo(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
//        if (deleteRequest == null || deleteRequest.getId() <= 0) {
//            throw new BusinessException(ErrorCode.PARAMS_ERROR);
//        }
//        User user = userService.getLoginUser(request);
//        long id = deleteRequest.getId();
//        // 判断是否存在
//        UserInterfaceInfo oldUserInterfaceInfo = userUserInterfaceInfoService.getById(id);
//        ThrowUtils.throwIf(oldUserInterfaceInfo == null, ErrorCode.NOT_FOUND_ERROR);
//        // 仅本人或管理员可删除
//        if (!oldUserInterfaceInfo.getUserId().equals(user.getId()) && !userService.isAdmin(request)) {
//            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
//        }
//        boolean b = userUserInterfaceInfoService.removeById(id);
//        return ResultUtils.success(b);
//    }
//
//    /**
//     * 更新（仅管理员）
//     *
//     * @param userUserInterfaceInfoUpdateRequest
//     * @return
//     */
//    @PostMapping("/update")
//    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
//    public BaseResponse<Boolean> updateUserInterfaceInfo(@RequestBody UserInterfaceInfoUpdateRequest userUserInterfaceInfoUpdateRequest) {
//        if (userUserInterfaceInfoUpdateRequest == null || userUserInterfaceInfoUpdateRequest.getId() <= 0) {
//            throw new BusinessException(ErrorCode.PARAMS_ERROR);
//        }
//        UserInterfaceInfo userUserInterfaceInfo = new UserInterfaceInfo();
//        BeanUtils.copyProperties(userUserInterfaceInfoUpdateRequest, userUserInterfaceInfo);
//
//        // 参数校验
//        userUserInterfaceInfoService.validUserInterfaceInfo(userUserInterfaceInfo, false);
//        long id = userUserInterfaceInfoUpdateRequest.getId();
//        // 判断是否存在
//        UserInterfaceInfo oldUserInterfaceInfo = userUserInterfaceInfoService.getById(id);
//        ThrowUtils.throwIf(oldUserInterfaceInfo == null, ErrorCode.NOT_FOUND_ERROR);
//        boolean result = userUserInterfaceInfoService.updateById(userUserInterfaceInfo);
//        return ResultUtils.success(result);
//    }
//
//    /**
//     * 根据 id 获取
//     *
//     * @param id
//     * @return
//     */
//    @GetMapping("/get/vo")
//    public BaseResponse<UserInterfaceInfoVO> getUserInterfaceInfoVOById(long id, HttpServletRequest request) {
//        if (id <= 0) {
//            throw new BusinessException(ErrorCode.PARAMS_ERROR);
//        }
//        UserInterfaceInfo userUserInterfaceInfo = userUserInterfaceInfoService.getById(id);
//        if (userUserInterfaceInfo == null) {
//            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
//        }
//        return ResultUtils.success(userUserInterfaceInfoService.getUserInterfaceInfoVO(userUserInterfaceInfo, request));
//    }
//
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
//        Page<UserInterfaceInfo> userUserInterfaceInfoPage = userUserInterfaceInfoService.page(new Page<>(current, size),
//                userUserInterfaceInfoService.getQueryWrapper(userUserInterfaceInfoQueryRequest));
//        return ResultUtils.success(userUserInterfaceInfoPage);
//    }
//
//    /**
//     * 分页获取列表（封装类）
//     *
//     * @param userUserInterfaceInfoQueryRequest
//     * @param request
//     * @return
//     */
//    @PostMapping("/list/page/vo")
//    public BaseResponse<Page<UserInterfaceInfoVO>> listUserInterfaceInfoVOByPage(@RequestBody UserInterfaceInfoQueryRequest userUserInterfaceInfoQueryRequest,
//            HttpServletRequest request) {
////        long current = userUserInterfaceInfoQueryRequest.getCurrent();
////        long size = userUserInterfaceInfoQueryRequest.getPageSize();
////        // 限制爬虫
////        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
////        Page<UserInterfaceInfo> userUserInterfaceInfoPage = userUserInterfaceInfoService.page(new Page<>(current, size),
////                userUserInterfaceInfoService.getQueryWrapper(userUserInterfaceInfoQueryRequest));
////        return ResultUtils.success(userUserInterfaceInfoService.getUserInterfaceInfoVOPage(userUserInterfaceInfoPage, request));
//        return null;
//    }
//
//
//    /**
//     * 发布（仅管理员）
//     *
//     * @param idRequest
//     * @return BaseResponse
//     */
//    @PostMapping("/online")
//    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
//    public BaseResponse<Boolean> onlineUserInterfaceInfo(@RequestBody IdRequest idRequest) {
//        if (idRequest == null || idRequest.getId() <= 0) {
//            throw new BusinessException(ErrorCode.PARAMS_ERROR);
//        }
//        Long id = idRequest.getId();
//        UserInterfaceInfo olduserUserInterfaceInfo = userUserInterfaceInfoService.getById(id);
//        if(olduserUserInterfaceInfo==null){
//            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
//        }
//
//
//        com.si1v3r.si1v3rapiclientsdk.model.User user=new com.si1v3r.si1v3rapiclientsdk.model.User();
//        user.setName("test");
//        String getUser = si1v3rApiClient.getUsernameByPost(user);
//        if(getUser==null){
//            throw new BusinessException(ErrorCode.PARAMS_ERROR,"接口认证失败");
//        }
//
//        UserInterfaceInfo userUserInterfaceInfo =new UserInterfaceInfo();
//        userUserInterfaceInfo.setId(id);
//        userUserInterfaceInfo.setStatus(InterfaceStatusEnum.ONLINE.getValue());
//
//        boolean result = userUserInterfaceInfoService.updateById(userUserInterfaceInfo);
//
//        return ResultUtils.success(result);
//    }
//
//
//    /**
//     * 下线（仅管理员）
//     *
//     * @param idRequest
//     * @return BaseResponse
//     */
//    @PostMapping("/offline")
//    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
//    public BaseResponse<Boolean> offlineUserInterfaceInfo(@RequestBody IdRequest idRequest) {
//        if (idRequest == null || idRequest.getId() <= 0) {
//            throw new BusinessException(ErrorCode.PARAMS_ERROR);
//        }
//        Long id = idRequest.getId();
//        UserInterfaceInfo olduserUserInterfaceInfo = userUserInterfaceInfoService.getById(id);
//        if(olduserUserInterfaceInfo==null){
//            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
//        }
//
//        UserInterfaceInfo userUserInterfaceInfo=new UserInterfaceInfo();
//        userUserInterfaceInfo.setId(id);
//        userUserInterfaceInfo.setStatus(InterfaceStatusEnum.OFFLINE.getValue());
//
//
//
//       boolean result = userUserInterfaceInfoService.updateById(userUserInterfaceInfo);
//
//
//        return ResultUtils.success(result);
//    }
//
//
//    /**
//     * 测试调用
//     *
//     * @param userUserInterfaceInfoInvokeRequest request
//     * @return BaseResponse
//     */
//    @PostMapping("/invoke")
//    public BaseResponse<Object> invokeUserInterfaceInfo(@RequestBody UserInterfaceInfoInvokeRequest userUserInterfaceInfoInvokeRequest,HttpServletRequest request) {
//        if (userUserInterfaceInfoInvokeRequest == null || userUserInterfaceInfoInvokeRequest.getId()<=0) {
//            throw new BusinessException(ErrorCode.PARAMS_ERROR);
//        }
//        Long id = userUserInterfaceInfoInvokeRequest.getId();
//        String requestParams = userUserInterfaceInfoInvokeRequest.getRequestParams();
//        UserInterfaceInfo olduserUserInterfaceInfo = userUserInterfaceInfoService.getById(id);
//        //判断是否存在
//        if(olduserUserInterfaceInfo==null){
//            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
//        }
//        if(olduserUserInterfaceInfo.getStatus()==InterfaceStatusEnum.OFFLINE.getValue()){
//            throw new BusinessException(ErrorCode.PARAMS_ERROR,"接口已下线");
//        }
//
//
//        User loginUser = userService.getLoginUser(request);
//        String assessKey=loginUser.getAssessKey();
//        String secretKey = loginUser.getSecretKey();
//        Si1v3rApiClient tempClient=new Si1v3rApiClient(assessKey,secretKey);
//
//        Gson gson=new Gson();
//        com.si1v3r.si1v3rapiclientsdk.model.User user = gson.fromJson(requestParams,com.si1v3r.si1v3rapiclientsdk.model.User.class);
//        String username = tempClient.getUsernameByPost(user);
//        return ResultUtils.success(username);
//    }
//
//
//
//}
