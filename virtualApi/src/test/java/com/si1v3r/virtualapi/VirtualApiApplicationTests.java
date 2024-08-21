package com.si1v3r.virtualapi;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import com.si1v3r.si1v3rapiclientsdk.client.Si1v3rApiClient;
import com.si1v3r.si1v3rapiclientsdk.model.User;

import javax.annotation.Resource;

@SpringBootTest
class VirtualApiApplicationTests {

    @Resource
    private Si1v3rApiClient si1v3rApiClient;

    @Test
    void contextLoads() {
    }

    @Test
    void clintTest(){

        String params="ruiming";
        User user1 = new User();
        user1.setName(params);

        String result = si1v3rApiClient.getNameByGet(params);
        String result2 = si1v3rApiClient.getNameByPost(params);
        String result3 = si1v3rApiClient.getUsernameByPost(user1);

        System.out.println(result);
        System.out.println(result2);
        System.out.println(result3);
    }

}
