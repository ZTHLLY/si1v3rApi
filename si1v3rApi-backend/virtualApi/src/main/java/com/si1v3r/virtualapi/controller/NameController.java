
package com.si1v3r.virtualapi.controller;



import com.si1v3r.si1v3rapiclientsdk.model.User;
import com.si1v3r.si1v3rapiclientsdk.utils.SignUtils;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

/**
 * @author si1v3r
 */
@RestController
@RequestMapping("/")
public class NameController {

    // http://127.0.0.1:8080/hello?name=lisi

    @GetMapping("/")
    public String getName(String name,HttpServletRequest httpServletRequest) {
        System.out.println(httpServletRequest.getHeader("si1v3r"));
        return "Get Hello " + name;
    }

    // http://127.0.0.1:8080/user
    @PostMapping("/name")
    public String postName(@RequestParam String name) {
        return "Post hello "+ name;
    }

    @PostMapping("/user")
    public String postName(@RequestBody User user, HttpServletRequest request) {
        String assessKey = request.getHeader("assessKey");
        //String secretKey = request.getHeader("secretKey");
        String nonce = request.getHeader("nonce");
        String body = request.getHeader("body");
        String timestamp = request.getHeader("timestamp");
        String sign = request.getHeader("sign");

        Map<String,String> headers=new HashMap<>();
        headers.put("nonce",nonce);
        headers.put("body",body);
        headers.put("timestamp",timestamp);
        //这个secretKey正规流程要从数据库里查，我这里先直接用了
        String secretKey="abcdefgh";

        String realSign = SignUtils.getSign(headers, secretKey);

        //校验assessKey
        if(!assessKey.equals("admin01")){
            throw new RuntimeException("no permission");
        }
        //校验签名
        if(!sign.equals(realSign)){
            throw new RuntimeException("no permission");
        }
        //这里其实还要校验别的像随机数啊什么有的没的，我先不校验了

        String result= "Post hello "+ user.getName()+" by post user";
        return result;
    }

}
