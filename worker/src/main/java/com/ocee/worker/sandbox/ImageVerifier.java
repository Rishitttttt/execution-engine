package com.ocee.worker.sandbox;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.exception.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class ImageVerifier {
    private static final Logger log = LoggerFactory.getLogger(ImageVerifier.class);
    private final DockerClient docker;

    public ImageVerifier(DockerClient docker) { this.docker = docker; }

    public void verify(List<String> images) {
        for (String img : images) {
            try {
                docker.inspectImageCmd(img).exec();
                log.info("Sandbox image present: {}", img);
            } catch (NotFoundException e) {
                throw new IllegalStateException(
                        "Sandbox image missing: " + img + " — build it before starting the worker.", e);
            }
        }
    }
}
