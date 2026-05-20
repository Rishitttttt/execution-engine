package com.ocee.worker.config;

import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.transport.DockerHttpClient;
import com.github.dockerjava.zerodep.ZerodepDockerHttpClient;
import com.ocee.worker.sandbox.SandboxProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.time.Duration;

@Configuration
@Profile("!mock-executor")
public class DockerClientConfig {

    @Bean(destroyMethod = "close")
    public DockerClient dockerClient(SandboxProperties props) {
        var cfg = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost(props.docker().socket())
                .build();
        DockerHttpClient http = new ZerodepDockerHttpClient.Builder()
                .dockerHost(cfg.getDockerHost())
                .maxConnections(64)
                .connectionTimeout(Duration.ofSeconds(5))
                .responseTimeout(Duration.ofSeconds(60))
                .build();
        return DockerClientImpl.getInstance(cfg, http);
    }
}
