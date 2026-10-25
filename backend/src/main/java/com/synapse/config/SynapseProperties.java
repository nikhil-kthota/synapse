package com.synapse.config;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "synapse")
public record SynapseProperties(Jwt jwt, Cors cors, Analysis analysis) {

    public record Jwt(String secret, Duration ttl) {
    }

    public record Cors(List<String> allowedOrigins) {
    }

    public record Analysis(Path workspace, int maxFiles, long maxFileBytes, long maxRepoBytes, AiFiles aiFiles) {
    }

    /** How many files get an AI summary for each analysis depth. */
    public record AiFiles(int quick, int standard, int deep) {
    }
}
