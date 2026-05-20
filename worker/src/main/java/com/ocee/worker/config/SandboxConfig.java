package com.ocee.worker.config;

import com.github.dockerjava.api.DockerClient;
import com.ocee.worker.sandbox.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.net.InetAddress;

@Configuration
@Profile("!mock-executor")
public class SandboxConfig {

    @Bean
    public String workerId() {
        try { return InetAddress.getLocalHost().getHostName() + "-" + ProcessHandle.current().pid(); }
        catch (Exception e) { return "worker-" + ProcessHandle.current().pid(); }
    }

    @Bean
    public VolumeManager volumeManager(DockerClient d, String workerId) {
        return new VolumeManager(d, workerId);
    }

    @Bean
    public ContainerRunner containerRunner(DockerClient d, String workerId, SandboxProperties p) {
        return new ContainerRunner(d, workerId, p.outputCapBytes());
    }

    @Bean
    public SandboxOrchestrator orchestrator(VolumeManager vm, ContainerRunner cr,
                                            String workerId, SandboxProperties p) {
        return new SandboxOrchestrator(vm, cr, workerId, p.outputCapBytes(), p.tmpfsBytes());
    }

    @Bean
    public ImageVerifier imageVerifier(DockerClient d) {
        return new ImageVerifier(d);
    }
}
