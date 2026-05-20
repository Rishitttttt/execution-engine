package com.ocee.worker.sandbox;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.Container;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Component
@Profile("!mock-executor")
public class SandboxReaper {

    private static final Logger log = LoggerFactory.getLogger(SandboxReaper.class);

    private final DockerClient docker;
    private final String workerId;
    private final long maxAgeSeconds;

    public SandboxReaper(DockerClient docker, String workerId, SandboxProperties props) {
        this.docker = docker;
        this.workerId = workerId;
        this.maxAgeSeconds = props.reaperMaxAge().toSeconds();
    }

    @PostConstruct
    public void reapForThisWorker() {
        log.info("Reaping orphaned sandbox resources for worker {}", workerId);
        removeContainers(Map.of(SandboxLabels.ROLE, SandboxLabels.ROLE_VALUE,
                                SandboxLabels.WORKER_ID, workerId), Long.MAX_VALUE);
        removeVolumes(SandboxLabels.WORKER_ID + "=" + workerId, Long.MAX_VALUE);
    }

    @Scheduled(fixedDelayString = "${sandbox.reaper-interval}")
    public void reapStale() {
        long olderThanEpoch = Instant.now().getEpochSecond() - maxAgeSeconds;
        removeContainers(Map.of(SandboxLabels.ROLE, SandboxLabels.ROLE_VALUE), olderThanEpoch);
        removeVolumes(SandboxLabels.ROLE + "=" + SandboxLabels.ROLE_VALUE, olderThanEpoch);
    }

    private void removeContainers(Map<String,String> labelFilter, long createdBeforeEpochSec) {
        List<Container> containers = docker.listContainersCmd()
                .withShowAll(true).withLabelFilter(labelFilter).exec();
        for (Container c : containers) {
            if (c.getCreated() != null && c.getCreated() > createdBeforeEpochSec) continue;
            try { docker.removeContainerCmd(c.getId()).withForce(true).exec(); }
            catch (NotFoundException ignored) {}
            catch (Exception e) { log.warn("Failed to remove container {}: {}", c.getId(), e.getMessage()); }
        }
    }

    private void removeVolumes(String labelKv, long createdBeforeEpochSec) {
        var resp = docker.listVolumesCmd().withFilter("label", List.of(labelKv)).exec();
        if (resp == null || resp.getVolumes() == null) return;
        for (var v : resp.getVolumes()) {
            // InspectVolumeResponse has no createdAt accessor in this client version;
            // the label filter already constrains us to sandbox volumes, and `removeVolume`
            // on a volume in use returns an error which we swallow.
            try { docker.removeVolumeCmd(v.getName()).exec(); }
            catch (Exception e) { log.debug("Skip volume {}: {}", v.getName(), e.getMessage()); }
        }
    }
}
