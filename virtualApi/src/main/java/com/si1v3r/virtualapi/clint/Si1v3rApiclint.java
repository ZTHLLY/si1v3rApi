package com.si1v3r.virtualapi.clint;


import java.util.HashMap;
import cn.hutool.http.HttpUtil;

public class Si1v3rApiclint {

    public String getNameByGet(String name) {
        //可以单独传入http参数，这样参数会自动做URL编码，拼接在URL中
        HashMap<String, Object> paramMap = new HashMap<>();
        paramMap.put("name", name);
        String result = HttpUtil.get("http://localhost:8002/api/name", paramMap);
        System.out.println(result);
        return result;
    }

}
