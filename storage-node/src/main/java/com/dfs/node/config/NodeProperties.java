package com.dfs.node.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "node")
@Data
public class NodeProperties {
    private String id;
    private String zone;
    private String storageRoot;
    private long totalCapacity;
}
