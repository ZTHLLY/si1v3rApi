package com.si1v3r.si1v3rapiclientsdk;


import com.si1v3r.si1v3rapiclientsdk.client.Si1v3rApiClient;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties("si1v3r.client")
@ComponentScan

public class si1v3rApiClientConfig {
    private String assessKey;

    private String secretKey;

    private String gatewayHost = "http://localhost:8090";
    private int connectTimeoutMillis = 5000;
    private int readTimeoutMillis = 10000;

    @Bean
    public Si1v3rApiClient si1v3rApiClient(){
        Si1v3rApiClient client = new Si1v3rApiClient(assessKey, secretKey, gatewayHost);
        client.setConnectTimeoutMillis(connectTimeoutMillis);
        client.setReadTimeoutMillis(readTimeoutMillis);
        return client;
    }

}
