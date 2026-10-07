package com.mathmap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MathMapApplication {
    public static void main(String[] args) {
        SpringApplication.run(MathMapApplication.class, args);
    }
}
