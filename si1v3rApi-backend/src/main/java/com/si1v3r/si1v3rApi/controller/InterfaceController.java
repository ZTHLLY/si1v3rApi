package com.si1v3r.si1v3rApi.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.si1v3r.si1v3rApi.model.vo.UserKeyVO;
import com.si1v3r.si1v3rApi.model.vo.InvokeResultVO;
import com.si1v3r.si1v3rapiclientsdk.model.ApiResponse;
import org.springframework.beans.factory.annotation.Value;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Iterator;
import com.si1v3r.si1v3rApi.annotation.AuthCheck;
import com.si1v3r.si1v3rApi.common.*;
import com.si1v3r.si1v3rApi.constant.UserConstant;
import com.si1v3r.si1v3rApi.exception.BusinessException;
import com.si1v3r.si1v3rApi.exception.ThrowUtils;
import com.si1v3r.si1v3rApi.model.dto.interfaceinfo.InterfaceInfoAddRequest;
import com.si1v3r.si1v3rApi.model.dto.interfaceinfo.InterfaceInfoInvokeRequest;
import com.si1v3r.si1v3rApi.model.dto.interfaceinfo.InterfaceInfoQueryRequest;
import com.si1v3r.si1v3rApi.model.dto.interfaceinfo.InterfaceInfoUpdateRequest;
import com.si1v3r.si1v3rApi.model.entity.InterfaceInfo;
import com.si1v3r.si1v3rApi.model.entity.User;
import com.si1v3r.si1v3rApi.model.enums.InterfaceStatusEnum;
import com.si1v3r.si1v3rApi.model.vo.InterfaceInfoVO;
import com.si1v3r.si1v3rApi.service.InterfaceInfoService;
import com.si1v3r.si1v3rApi.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;
import com.si1v3r.si1v3rapiclientsdk.client.Si1v3rApiClient;


import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;

/**
 * 帖子接口
 *
 * @author si1v3r
 */
@RestController
@RequestMapping("/interfaceInfo")
@Slf4j
public class InterfaceController {

    @Resource
    private InterfaceInfoService interfaceInfoService;

    @Resource
    private UserService userService;


    @Value("${si1v3r.client.gateway-host:http://localhost:8090}")
    private String gatewayHost = "http://localhost:8090";

    // region 增删改查

    /**
     * 创建
     *
     * @param interfaceInfoAddRequest
     * @param request
     * @return
     */
    @PostMapping("/add")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Long> addInterfaceInfo(@RequestBody InterfaceInfoAddRequest interfaceInfoAddRequest, HttpServletRequest request) {
        if (interfaceInfoAddRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        //新建要添加的对象
        InterfaceInfo interfaceInfo = new InterfaceInfo();
        BeanUtils.copyProperties(interfaceInfoAddRequest, interfaceInfo);

        interfaceInfoService.validInterfaceInfo(interfaceInfo, true);
        User loginUser = userService.getLoginUser(request);
        interfaceInfo.setUserId(loginUser.getId());

        boolean result = interfaceInfoService.save(interfaceInfo);

        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        long newInterfaceInfoId = interfaceInfo.getId();
        return ResultUtils.success(newInterfaceInfoId);
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
    public BaseResponse<Boolean> deleteInterfaceInfo(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        long id = deleteRequest.getId();
        // 判断是否存在
        InterfaceInfo oldInterfaceInfo = interfaceInfoService.getById(id);
        ThrowUtils.throwIf(oldInterfaceInfo == null, ErrorCode.NOT_FOUND_ERROR);
        boolean b = interfaceInfoService.removeById(id);
        return ResultUtils.success(b);
    }

    /**
     * 更新（仅管理员）
     *
     * @param interfaceInfoUpdateRequest
     * @return
     */
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateInterfaceInfo(@RequestBody InterfaceInfoUpdateRequest interfaceInfoUpdateRequest) {
        if (interfaceInfoUpdateRequest == null || interfaceInfoUpdateRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        InterfaceInfo interfaceInfo = new InterfaceInfo();
        BeanUtils.copyProperties(interfaceInfoUpdateRequest, interfaceInfo);

        // 参数校验
        interfaceInfoService.validInterfaceInfo(interfaceInfo, false);
        long id = interfaceInfoUpdateRequest.getId();
        // 判断是否存在
        InterfaceInfo oldInterfaceInfo = interfaceInfoService.getById(id);
        ThrowUtils.throwIf(oldInterfaceInfo == null, ErrorCode.NOT_FOUND_ERROR);
        boolean result = interfaceInfoService.updateById(interfaceInfo);
        return ResultUtils.success(result);
    }

    /**
     * 根据 id 获取
     *
     * @param id
     * @return
     */
    @GetMapping("/get/vo")
    public BaseResponse<InterfaceInfoVO> getInterfaceInfoVOById(long id, HttpServletRequest request) {
        if (id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        InterfaceInfo interfaceInfo = interfaceInfoService.getById(id);
        if (interfaceInfo == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        return ResultUtils.success(interfaceInfoService.getInterfaceInfoVO(interfaceInfo, request));
    }

    /**
     * 分页获取列表（仅管理员）
     *
     * @param interfaceInfoQueryRequest
     * @return
     */
    @PostMapping("/list/page")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<InterfaceInfo>> listInterfaceInfoByPage(@RequestBody InterfaceInfoQueryRequest interfaceInfoQueryRequest) {
        long current = interfaceInfoQueryRequest.getCurrent();
        long size = interfaceInfoQueryRequest.getPageSize();
        Page<InterfaceInfo> interfaceInfoPage = interfaceInfoService.page(new Page<>(current, size),
                interfaceInfoService.getQueryWrapper(interfaceInfoQueryRequest));
        return ResultUtils.success(interfaceInfoPage);
    }

    /**
     * 分页获取列表（封装类）
     *
     * @param interfaceInfoQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/list/page/vo")
    public BaseResponse<Page<InterfaceInfoVO>> listInterfaceInfoVOByPage(@RequestBody InterfaceInfoQueryRequest interfaceInfoQueryRequest,
            HttpServletRequest request) {
        userService.getLoginUser(request);
        ThrowUtils.throwIf(interfaceInfoQueryRequest == null, ErrorCode.PARAMS_ERROR);
        long current = interfaceInfoQueryRequest.getCurrent();
        long size = interfaceInfoQueryRequest.getPageSize();
        ThrowUtils.throwIf(current < 1 || size < 1 || size > 20, ErrorCode.PARAMS_ERROR);
        Page<InterfaceInfo> page = interfaceInfoService.page(new Page<>(current, size),
                interfaceInfoService.getQueryWrapper(interfaceInfoQueryRequest)
                        .eq("status", InterfaceStatusEnum.ONLINE.getValue())
                        .eq("isDelete", 0)
                        .like(StringUtils.isNotBlank(interfaceInfoQueryRequest.getDescription()),
                                "description", interfaceInfoQueryRequest.getDescription()));
        Page<InterfaceInfoVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(page.getRecords().stream().map(InterfaceInfoVO::objToVo).collect(Collectors.toList()));
        return ResultUtils.success(voPage);
    }


    /**
     * 发布（仅管理员）
     *
     * @param idRequest
     * @return BaseResponse
     */
    @PostMapping("/online")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> onlineInterfaceInfo(@RequestBody IdRequest idRequest) {
        if (idRequest == null || idRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Long id = idRequest.getId();
        InterfaceInfo oldinterfaceInfo = interfaceInfoService.getById(id);
        if(oldinterfaceInfo==null){
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }


        interfaceInfoService.validInterfaceInfo(oldinterfaceInfo, true);

        InterfaceInfo interfaceInfo =new InterfaceInfo();
        interfaceInfo.setId(id);
        interfaceInfo.setStatus(InterfaceStatusEnum.ONLINE.getValue());
        interfaceInfo.setMethod(oldinterfaceInfo.getMethod());

        boolean result = interfaceInfoService.updateById(interfaceInfo);

        return ResultUtils.success(result);
    }


    /**
     * 下线（仅管理员）
     *
     * @param idRequest
     * @return BaseResponse
     */
    @PostMapping("/offline")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> offlineInterfaceInfo(@RequestBody IdRequest idRequest) {
        if (idRequest == null || idRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Long id = idRequest.getId();
        InterfaceInfo oldinterfaceInfo = interfaceInfoService.getById(id);
        if(oldinterfaceInfo==null){
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }

        InterfaceInfo interfaceInfo=new InterfaceInfo();
        interfaceInfo.setId(id);
        interfaceInfo.setStatus(InterfaceStatusEnum.OFFLINE.getValue());



       boolean result = interfaceInfoService.updateById(interfaceInfo);


        return ResultUtils.success(result);
    }


    /**
     * 测试调用
     *
     * @param interfaceInfoInvokeRequest request
     * @return BaseResponse
     */
    @PostMapping("/invoke")
    public BaseResponse<InvokeResultVO> invokeInterfaceInfo(@RequestBody InterfaceInfoInvokeRequest input, HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        if (input == null || input.getId() == null || input.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        InterfaceInfo info = interfaceInfoService.getById(input.getId());
        ThrowUtils.throwIf(info == null, ErrorCode.NOT_FOUND_ERROR);
        if (!Integer.valueOf(InterfaceStatusEnum.ONLINE.getValue()).equals(info.getStatus())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "接口已下线");
        }
        String method = info.getMethod();
        String params = input.getRequestParams();
        Map<String, String> query = new LinkedHashMap<>();
        String body = null;
        try {
            JsonNode json = StringUtils.isBlank(params) ? null : new ObjectMapper()
                    .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(params);
            if ("GET".equals(method) || "DELETE".equals(method)) {
                if (json != null) {
                    if (!json.isObject()) {
                        throw new IllegalArgumentException();
                    }
                    Iterator<Map.Entry<String, JsonNode>> fields = json.fields();
                    while (fields.hasNext()) {
                        Map.Entry<String, JsonNode> field = fields.next();
                        if (field.getValue().isContainerNode()) {
                            throw new IllegalArgumentException();
                        }
                        query.put(field.getKey(), field.getValue().asText());
                    }
                }
            } else if ("POST".equals(method) || "PUT".equals(method)) {
                body = StringUtils.isBlank(params) ? "{}" : params;
            } else {
                throw new IllegalArgumentException();
            }
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "requestParams 不合法");
        }
        UserKeyVO keys = userService.getUserKey(loginUser.getId(), false);
        Si1v3rApiClient client = new Si1v3rApiClient(keys.getAssessKey(), keys.getSecretKey(), gatewayHost);
        ApiResponse response = client.invoke(method, info.getUrl(), query, body);
        InvokeResultVO result = new InvokeResultVO();
        result.setStatus(response.getStatus());
        result.setBody(response.getBody());
        return ResultUtils.success(result);
    }
}
