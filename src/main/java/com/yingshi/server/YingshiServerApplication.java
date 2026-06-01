package com.yingshi.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class YingshiServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(YingshiServerApplication.class, args);
    }

}
