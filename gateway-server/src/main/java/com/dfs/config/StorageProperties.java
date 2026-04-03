package com.dfs.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "storage")
@Data
public class StorageProperties {

    private List<NodeConfig> nodes;
    private int replicationFactor = 2;
    private int chunkSizeBytes = 1048576; // 1 MB
    private long healthCheckInterval = 30000;
    private int nodeTimeout = 5000;

    @Data
    public static class NodeConfig {
        private String id;
        private String url;
        private String zone;
    }
}
