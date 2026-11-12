
package com.si1v3r.virtualapi.controller;



import com.si1v3r.si1v3rapiclientsdk.model.User;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;

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
        String result= "Post hello "+ user.getName()+" by post user";
        return result;
    }

}
