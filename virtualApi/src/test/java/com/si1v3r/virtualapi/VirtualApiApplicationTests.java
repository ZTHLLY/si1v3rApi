package com.si1v3r.virtualapi;

import com.si1v3r.virtualapi.clint.Si1v3rApiclint;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class VirtualApiApplicationTests {

    @Test
    void contextLoads() {
    }

    @Test
    void clintTest(){
        Si1v3rApiclint si1v3rApiclint=new Si1v3rApiclint();
        String result = si1v3rApiclint.getNameByGet("si1v3r");
        System.out.println(result);
    }

}
