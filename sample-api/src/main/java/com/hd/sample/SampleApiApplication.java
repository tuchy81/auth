package com.hd.sample;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;

@SpringBootApplication
public class SampleApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(SampleApiApplication.class, args);
    }

    @Bean
    public RestClient authzRestClient(@org.springframework.beans.factory.annotation.Value("${authz.service-url}") String url) {
        return RestClient.builder().baseUrl(url).build();
    }
}
