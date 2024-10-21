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

    @Bean
    public Si1v3rApiClient si1v3rApiClient(){
        return new Si1v3rApiClient(assessKey,secretKey);
    }

}
