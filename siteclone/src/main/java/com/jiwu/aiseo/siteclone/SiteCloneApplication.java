package com.jiwu.aiseo.siteclone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class SiteCloneApplication {

    public static void main(String[] args) {
        SpringApplication.run(SiteCloneApplication.class, args);
    }
}
