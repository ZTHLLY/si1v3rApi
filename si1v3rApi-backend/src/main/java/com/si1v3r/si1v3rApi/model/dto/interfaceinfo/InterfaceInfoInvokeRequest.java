package com.si1v3r.si1v3rApi.model.dto.interfaceinfo;

import lombok.Data;

import java.io.Serializable;

/**
 * 测试调用
 *
 * @author si1v3r
 */
@Data
public class InterfaceInfoInvokeRequest implements Serializable {



    /**
     * id
     */
    private Long id;

    /**
     * 请求参数
     */
    private String requestParams;





    private static final long serialVersionUID = 1L;
}