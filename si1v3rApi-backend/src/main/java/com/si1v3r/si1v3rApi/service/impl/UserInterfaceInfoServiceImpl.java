package com.si1v3r.si1v3rApi.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.si1v3r.si1v3rApi.common.ErrorCode;
import com.si1v3r.si1v3rApi.exception.BusinessException;
import com.si1v3r.si1v3rApi.model.entity.UserInterfaceInfo;
import com.si1v3r.si1v3rApi.model.vo.MyInvokeStatVO;
import java.util.List;
import com.si1v3r.si1v3rApi.mapper.UserInterfaceInfoMapper;
import com.si1v3r.si1v3rApi.service.UserInterfaceInfoService;
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

        if (add && (userinterfaceInfo.getUserId() == null || userinterfaceInfo.getUserId() <= 0
                || userinterfaceInfo.getInterfaceInfoId() == null || userinterfaceInfo.getInterfaceInfoId() <= 0)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "User or Interface not exist");
        }
        if (userinterfaceInfo.getTotalNum() != null && userinterfaceInfo.getTotalNum() < 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "total number can not be less than 0");
        }
    }

    @Override
    public List<MyInvokeStatVO> listMyInvokeStats(long userId) {
        if (userId <= 0) throw new BusinessException(ErrorCode.PARAMS_ERROR);
        return baseMapper.listMyInvokeStats(userId);
    }

    @Override
    public boolean invokeCount(long interfaceInfoId, long userId) {
        if (interfaceInfoId <= 0 || userId <= 0) return false;
        return baseMapper.recordInvoke(interfaceInfoId, userId) >= 1;
    }
}
