package com.si1v3r.si1v3rApi.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.si1v3r.si1v3rApi.common.ErrorCode;
import com.si1v3r.si1v3rApi.exception.BusinessException;
import com.si1v3r.si1v3rApi.exception.ThrowUtils;
import com.si1v3r.si1v3rApi.model.entity.UserInterfaceInfo;
import com.si1v3r.si1v3rApi.mapper.UserInterfaceInfoMapper;
import com.si1v3r.si1v3rApi.service.UserInterfaceInfoService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

/**
* @author silverbullets
* @description 针对表【user_interface_info(用户调用接口关系)】的数据库操作Service实现
* @createDate 2024-10-15 17:28:14
*/
@Service
public class UserInterfaceInfoServiceImpl extends ServiceImpl<UserInterfaceInfoMapper, UserInterfaceInfo>
    implements UserInterfaceInfoService{

    @Override
    public void validUserInterfaceInfo(UserInterfaceInfo userinterfaceInfo, boolean add) {
        if (userinterfaceInfo == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        // 创建时，参数不能为空
        if (add) {
            // 有参数则校验
            if (userinterfaceInfo.getId()<=0||userinterfaceInfo.getUserId()<=0) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "User or Interface not exist");
            }
        }
        if (userinterfaceInfo.getLeftNum()<0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "left number can not be less than 0");
        }
    }

    @Override
    public boolean invokeCount(long interfaceInfoId, long userId) {
        //校验
        if(interfaceInfoId<=0||userId<=0){
            throw  new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        UpdateWrapper<UserInterfaceInfo> updateWrapper=new UpdateWrapper<>();
        updateWrapper.eq("interfaceInfoId",interfaceInfoId);
        updateWrapper.eq("userId",userId);
        updateWrapper.setSql("leftNum=leftNum-1,totalNum=totalNum+1");
        return this.update(updateWrapper);
    }
}




