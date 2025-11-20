package com.fiap.azure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import lombok.Data;

@Data
@Configuration
@ConfigurationProperties(prefix = "azure.cli")
public class AzureCliConfig {
    private String path = "/usr/local/bin/az";
    private long timeout = 30000L;
}
