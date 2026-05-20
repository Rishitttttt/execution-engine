package com.ocee.worker.sandbox;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectImageCmd;
import com.github.dockerjava.api.command.InspectImageResponse;
import com.github.dockerjava.api.exception.NotFoundException;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class ImageCacheTest {
    @Test void presentImageIsCachedAfterFirstCall() {
        DockerClient d = mock(DockerClient.class);
        InspectImageCmd cmd = mock(InspectImageCmd.class);
        when(d.inspectImageCmd(anyString())).thenReturn(cmd);
        when(cmd.exec()).thenReturn(new InspectImageResponse());
        ImageCache c = new ImageCache(d);
        c.verifyAvailable("ocee/sandbox-python:3.11");
        c.verifyAvailable("ocee/sandbox-python:3.11");
        verify(d, times(1)).inspectImageCmd("ocee/sandbox-python:3.11");
    }

    @Test void missingImageThrows() {
        DockerClient d = mock(DockerClient.class);
        InspectImageCmd cmd = mock(InspectImageCmd.class);
        when(d.inspectImageCmd(anyString())).thenReturn(cmd);
        when(cmd.exec()).thenThrow(new NotFoundException("nope"));
        assertThatThrownBy(() -> new ImageCache(d).verifyAvailable("ocee/missing:1"))
                .isInstanceOf(SandboxImageMissingException.class)
                .hasMessageContaining("ocee/missing:1");
    }
}
