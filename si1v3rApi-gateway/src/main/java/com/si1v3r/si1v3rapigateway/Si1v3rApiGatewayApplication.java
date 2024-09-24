package com.si1v3r.si1v3rapigateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class Si1v3rApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(Si1v3rApiGatewayApplication.class, args);
    }

        @Bean
        public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
            return builder.routes()
                    .route("toBaidu", r -> r.path("/baidu")
                            .uri("http://www.baidu.com"))
                    .route("tosi1v3rApi", r -> r.host("*.myhost.org")
                            .uri("http://httpbin.org"))
                    .build();
        }

}
