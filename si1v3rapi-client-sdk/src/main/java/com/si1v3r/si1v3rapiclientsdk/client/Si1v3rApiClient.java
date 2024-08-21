package com.si1v3r.si1v3rapiclientsdk.client;


import cn.hutool.core.util.RandomUtil;
import cn.hutool.crypto.digest.DigestAlgorithm;
import cn.hutool.crypto.digest.Digester;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONUtil;

import com.si1v3r.si1v3rapiclientsdk.model.User;


import java.util.HashMap;
import java.util.Map;

public class Si1v3rApiClient {

    private String assessKey;
    private String secretKey;


    public Si1v3rApiClient(String assessKey, String secretKey){
        this.assessKey=assessKey;
        this.secretKey=secretKey;
    }

    public String getNameByGet(String name) {
        //可以单独传入http参数，这样参数会自动做URL编码，拼接在URL中
        HashMap<String, Object> paramMap = new HashMap<>();
        paramMap.put("name", name);
        String result = HttpUtil.get("http://localhost:8002/api/name/", paramMap);
        System.out.println(result);
        return result;
    }

    public String getNameByPost(String name) {
        //可以单独传入http参数，这样参数会自动做URL编码，拼接在URL中
        HashMap<String, Object> paramMap = new HashMap<>();
        paramMap.put("name", name);
        String result = HttpUtil.post("http://localhost:8002/api/name/", paramMap);
        System.out.println(result);
        return result;
    }

    /**
     * 生成签名
     * @param headers
     * @param secretKey
     * @return 加密签名
     */
    private String getSign(Map<String,String> headers,String secretKey){
        Digester md5=new Digester(DigestAlgorithm.SHA256);
        String nonce = headers.get("nonce");
        String body = headers.get("body");
        String timestamp = headers.get("timestamp");
        String content=nonce+'.'+body+'.'+timestamp+'.'+secretKey;
        return md5.digestHex(content);
    }

    /**
     * 设置请求头，要注意一定不能将secretKey发送给后端
     * @return headers
     */
    private Map<String,String> setKeys(String body){
        Map<String,String> headers=new HashMap<>();
        headers.put("assessKey",assessKey);
        //secretKey一定不能传出去
        //headers.put("secretKey",secretKey);
        headers.put("nonce", RandomUtil.randomNumbers(4));
        headers.put("body",body);
        headers.put("timestamp",String.valueOf(System.currentTimeMillis()/1000));
        headers.put("sign",getSign(headers,secretKey));
        return headers;
    }

    public String getUsernameByPost( User user) {
        String json = JSONUtil.toJsonStr(user);
        HttpResponse httpResponse = HttpRequest.post("http://localhost:8002/api/name/user")
                .addHeaders(setKeys(json))
                .body(json)
                .execute();
        System.out.println(httpResponse.getStatus());
        String result = httpResponse.body();
        System.out.println(result);
        return result;
    }

}
