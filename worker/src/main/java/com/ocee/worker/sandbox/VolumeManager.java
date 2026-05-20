package com.ocee.worker.sandbox;

import com.github.dockerjava.api.DockerClient;

import java.util.Map;

public class VolumeManager {
    private final DockerClient docker;
    private final String workerId;

    public VolumeManager(DockerClient docker, String workerId) {
        this.docker = docker;
        this.workerId = workerId;
    }

    public String create(String token) {
        String name = volumeName(token);
        docker.createVolumeCmd()
                .withName(name)
                .withLabels(Map.of(
                        SandboxLabels.ROLE, SandboxLabels.ROLE_VALUE,
                        SandboxLabels.TOKEN, token,
                        SandboxLabels.WORKER_ID, workerId))
                .exec();
        return name;
    }

    public void remove(String token) {
        try { docker.removeVolumeCmd(volumeName(token)).exec(); }
        catch (com.github.dockerjava.api.exception.NotFoundException ignored) {}
    }

    public static String volumeName(String token) { return "ocee-sandbox-" + token; }
}
