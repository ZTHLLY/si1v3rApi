package com.si1v3r.si1v3rApi.model.dto.userinterfaceinfo;

import lombok.Data;

import java.io.Serializable;


/**
 * 更新请求
 *
 * @author si1v3r
 */
@Data
public class UserInterfaceInfoUpdateRequest implements Serializable {

    /**
     * 主键
     */
    private Long id;

    /**
     * 总调用次数
     */
    private Integer totalNum;




}