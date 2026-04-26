package com.hd.authz;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableJpaRepositories(basePackages = "com.hd.authz.repo")
public class AuthzServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AuthzServiceApplication.class, args);
    }
}
