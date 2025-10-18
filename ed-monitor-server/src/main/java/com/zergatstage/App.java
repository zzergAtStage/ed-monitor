package com.zergatstage;


import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@ComponentScan("com.zergatstage.server")
@EntityScan(basePackages = {"com.zergatstage.domain"})
@EnableJpaRepositories(basePackages = {"com.zergatstage.server.repository", "com.zergatstage.domain"})
public class App {
    public static void main(String[] args) {
        SpringApplication.run(App.class, args);
    }
}
