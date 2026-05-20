package com.ocee.worker.sandbox;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

public class OutputCapturer {
    private final int capPerStream;
    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final ByteArrayOutputStream err = new ByteArrayOutputStream();
    private boolean truncated = false;

    public OutputCapturer(int capPerStream) { this.capPerStream = capPerStream; }

    public synchronized void appendStdout(byte[] buf, int len) { append(out, buf, len); }
    public synchronized void appendStderr(byte[] buf, int len) { append(err, buf, len); }

    private void append(ByteArrayOutputStream sink, byte[] buf, int len) {
        int remaining = capPerStream - sink.size();
        if (remaining <= 0) { truncated = true; return; }
        int toWrite = Math.min(len, remaining);
        sink.write(buf, 0, toWrite);
        if (toWrite < len) truncated = true;
    }

    public String stdout()    { return out.toString(StandardCharsets.UTF_8); }
    public String stderr()    { return err.toString(StandardCharsets.UTF_8); }
    public boolean truncated(){ return truncated; }
}
