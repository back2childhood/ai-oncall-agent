package com.oncall.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class OncallAgentApplication {
    public static void main(String[] args) {
        SpringApplication.run(OncallAgentApplication.class, args);
    }
}
