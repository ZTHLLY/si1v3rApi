package com.si1v3r.si1v3rApi.model.enums;

import org.apache.commons.lang3.ObjectUtils;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 接口状态枚举
 *
 * @author <a href="https://github.com/liyupi">程序员鱼皮</a>
 * @from <a href="https://yupi.icu">编程导航知识星球</a>
 */
public enum InterfaceStatusEnum {

    ONLINE("发布", 1),
    OFFLINE("下线", 0);

    private final String text;

    private final int value;

    InterfaceStatusEnum(String text, int value) {
        this.text = text;
        this.value = value;
    }



    public int getValue() {
        return value;
    }

    public String getText() {
        return text;
    }
}
