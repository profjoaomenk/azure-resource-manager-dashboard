package com.fiap.azure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import lombok.Data;
import java.io.File;

@Data
@Configuration
@ConfigurationProperties(prefix = "azure.cli")
public class AzureCliConfig {
private String path;
private long timeout = 30000L;
public AzureCliConfig() {
    this.path = detectAzureCliPath();
}

private String detectAzureCliPath() {
    String osName = System.getProperty("os.name").toLowerCase();
    
    if (osName.contains("win")) {
        return detectWindowsPath();
    } else if (osName.contains("mac")) {
        return detectMacPath();
    } else {
        return detectLinuxPath();
    }
}

private String detectWindowsPath() {
    String[] possiblePaths = {
        "C:\\Program Files (x86)\\Microsoft SDKs\\Azure\\CLI2\\wbin\\az.cmd",
        "C:\\Program Files\\Microsoft SDKs\\Azure\\CLI2\\wbin\\az.cmd",
    };

    for (String p : possiblePaths) {
        if (new File(p).exists()) {
            return p;
        }
    }

    return "az.cmd";
}

private String detectMacPath() {
    String[] possiblePaths = {
        "/opt/homebrew/bin/az",
        "/usr/local/bin/az",
    };

    for (String p : possiblePaths) {
        if (new File(p).exists()) {
            return p;
        }
    }

    return "az";
}

private String detectLinuxPath() {
    String[] possiblePaths = {
        "/usr/local/bin/az",
        "/usr/bin/az",
        "/snap/bin/az",
    };

    for (String p : possiblePaths) {
        if (new File(p).exists()) {
            return p;
        }
    }

    return "az";
}

public String getEffectivePath() {
    if (path != null && !path.isEmpty() && new File(path).exists()) {
        return path;
    }
    
    String osName = System.getProperty("os.name").toLowerCase();
    return osName.contains("win") ? "az.cmd" : "az";
}

}
