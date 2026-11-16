package com.si1v3r.si1v3rApi.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Insert;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.si1v3r.si1v3rApi.model.entity.UserInterfaceInfo;

/**
* @author silverbullets
* @description 针对表【user_interface_info(用户调用接口关系)】的数据库操作Mapper
* @createDate 2024-10-15 17:28:14
* @Entity generator.domain.UserInterfaceInfo
*/
public interface UserInterfaceInfoMapper extends BaseMapper<UserInterfaceInfo> {
    @Insert("INSERT INTO user_interface_info (userId, interfaceInfoId, totalNum) "
            + "VALUES (#{userId}, #{interfaceInfoId}, 1) "
            + "ON DUPLICATE KEY UPDATE totalNum = totalNum + 1, isDelete = 0")
    int recordInvoke(@Param("interfaceInfoId") long interfaceInfoId, @Param("userId") long userId);


}




