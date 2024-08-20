package com.si1v3r.virtualapi.utils;

import cn.hutool.crypto.digest.DigestAlgorithm;
import cn.hutool.crypto.digest.Digester;

import java.util.Map;

public class SignUtils {
    /**
     * 生成签名
     * @param headers
     * @param secretKey
     * @return 加密签名
     */
    public static String getSign(Map<String,String> headers, String secretKey){
        Digester md5=new Digester(DigestAlgorithm.SHA256);
        String nonce = headers.get("nonce");
        String body = headers.get("body");
        String timestamp = headers.get("timestamp");
        String content=nonce+'.'+body+'.'+timestamp+'.'+secretKey;
        return md5.digestHex(content);
    }

}
