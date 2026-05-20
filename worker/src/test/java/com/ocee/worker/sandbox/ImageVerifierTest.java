package com.ocee.worker.sandbox;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectImageCmd;
import com.github.dockerjava.api.command.InspectImageResponse;
import com.github.dockerjava.api.exception.NotFoundException;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ImageVerifierTest {
    @Test
    void presentImagesPass() {
        DockerClient d = mock(DockerClient.class);
        InspectImageCmd cmd = mock(InspectImageCmd.class);
        when(d.inspectImageCmd(anyString())).thenReturn(cmd);
        when(cmd.exec()).thenReturn(new InspectImageResponse());
        new ImageVerifier(d).verify(java.util.List.of("ocee/sandbox-python:3.11"));
    }
    @Test
    void missingImageThrows() {
        DockerClient d = mock(DockerClient.class);
        InspectImageCmd cmd = mock(InspectImageCmd.class);
        when(d.inspectImageCmd(anyString())).thenReturn(cmd);
        when(cmd.exec()).thenThrow(new NotFoundException("nope"));
        assertThatThrownBy(() ->
                new ImageVerifier(d).verify(java.util.List.of("ocee/missing:1")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ocee/missing:1");
    }
}
