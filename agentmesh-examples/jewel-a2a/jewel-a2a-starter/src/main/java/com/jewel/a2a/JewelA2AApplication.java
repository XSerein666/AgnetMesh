package com.jewel.a2a;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class JewelA2AApplication {

    public static void main(String[] args) {
        SpringApplication.run(JewelA2AApplication.class, args);
    }
}
