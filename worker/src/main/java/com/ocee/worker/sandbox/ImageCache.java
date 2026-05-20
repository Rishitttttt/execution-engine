package com.ocee.worker.sandbox;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.exception.NotFoundException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

@Component
@Profile("!mock-executor")
public class ImageCache {
    private final DockerClient docker;
    private final ConcurrentHashMap<String, Boolean> seen = new ConcurrentHashMap<>();

    public ImageCache(DockerClient docker) { this.docker = docker; }

    public void verifyAvailable(String image) {
        if (Boolean.TRUE.equals(seen.get(image))) return;
        try {
            docker.inspectImageCmd(image).exec();
            seen.put(image, true);
        } catch (NotFoundException e) {
            throw new SandboxImageMissingException(image);
        }
    }
}
