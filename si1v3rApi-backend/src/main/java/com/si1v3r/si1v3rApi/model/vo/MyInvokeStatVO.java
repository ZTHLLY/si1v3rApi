package com.si1v3r.si1v3rApi.model.vo;

import lombok.Data;
import java.io.Serializable;
import java.util.Date;

@Data
public class MyInvokeStatVO implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long interfaceInfoId;
    private String interfaceName;
    private String url;
    private String method;
    private Integer totalNum;
    private Date updateTime;
}
