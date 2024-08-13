package com.si1v3r.si1v3rApi.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.si1v3r.si1v3rApi.model.entity.InterfaceInfo;
import com.si1v3r.si1v3rApi.model.entity.Post;
import com.si1v3r.si1v3rApi.model.vo.InterfaceInfoVO;

import javax.servlet.http.HttpServletRequest;

/**
* @author ruimingzhu
* @description 针对表【interface_info(接口信息)】的数据库操作Service
* @createDate 2024-08-13 09:21:38
*/
public interface InterfaceInfoService extends IService<InterfaceInfo> {
    void validInterfaceInfo(InterfaceInfo interfaceInfo, boolean add);

    InterfaceInfoVO getInterfaceInfoVO(InterfaceInfo interfaceInfo, HttpServletRequest request);

}
