package com.si1v3r.virtualapi;

import com.si1v3r.virtualapi.clint.Si1v3rApiclint;
import com.si1v3r.virtualapi.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class VirtualApiApplicationTests {

    @Test
    void contextLoads() {
    }

    @Test
    void clintTest(){
        String params="ruiming";
        User user1 = new User();
        user1.setName(params);


        Si1v3rApiclint si1v3rApiclint=new Si1v3rApiclint();
        String result = si1v3rApiclint.getNameByGet(params);
        String result2 = si1v3rApiclint.getNameByPost(params);
        String result3 = si1v3rApiclint.getUsernameByPost(user1);

        System.out.println(result);
        System.out.println(result2);
        System.out.println(result3);
    }

}
