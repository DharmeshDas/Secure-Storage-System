package com.dfs.node;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties
public class StorageNodeApplication {
    public static void main(String[] args) {
        SpringApplication.run(StorageNodeApplication.class, args);
    }
}
