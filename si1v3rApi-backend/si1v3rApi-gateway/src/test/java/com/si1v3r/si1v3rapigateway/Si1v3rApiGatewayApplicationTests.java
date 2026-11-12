package com.si1v3r.si1v3rapigateway;

import com.si1v3r.si1v3rapigateway.filter.AuthGlobalFilter;
import org.apache.dubbo.spring.boot.autoconfigure.DubboAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@SpringBootTest(classes = Si1v3rApiGatewayApplicationTests.OfflineGateway.class)
class Si1v3rApiGatewayApplicationTests {
    // Keep the reactive gateway context smoke independent of Nacos/Dubbo providers.
    // AuthGlobalFilterTest exercises the real filter with a mocked InnerUserService.
    @Configuration
    @EnableAutoConfiguration(exclude = DubboAutoConfiguration.class)
    @Import(AuthGlobalFilter.class)
    static class OfflineGateway { }

    @Test
    void contextLoads() { }
}
