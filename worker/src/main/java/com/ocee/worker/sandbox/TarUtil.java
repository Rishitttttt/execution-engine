package com.ocee.worker.sandbox;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

final class TarUtil {
    private TarUtil() {}

    static byte[] singleFile(String name, String content) {
        byte[] data = content == null ? new byte[0] : content.getBytes(StandardCharsets.UTF_8);
        byte[] header = new byte[512];
        byte[] nameBytes = name.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(nameBytes, 0, header, 0, Math.min(nameBytes.length, 100));
        writeOctal(header, 100, 8, 0644);
        writeOctal(header, 108, 8, 1000);
        writeOctal(header, 116, 8, 1000);
        writeOctal(header, 124, 12, data.length);
        writeOctal(header, 136, 12, System.currentTimeMillis() / 1000);
        for (int i = 148; i < 156; i++) header[i] = ' ';
        header[156] = '0';
        byte[] magic = "ustar\0".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(magic, 0, header, 257, magic.length);
        header[263] = '0'; header[264] = '0';
        int checksum = 0;
        for (byte b : header) checksum += (b & 0xff);
        writeOctal(header, 148, 7, checksum);
        header[155] = 0;

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(header, 0, 512);
        out.write(data, 0, data.length);
        int pad = (512 - (data.length % 512)) % 512;
        out.write(new byte[pad], 0, pad);
        out.write(new byte[1024], 0, 1024);
        return out.toByteArray();
    }

    private static void writeOctal(byte[] dst, int off, int len, long value) {
        String s = Long.toOctalString(value);
        while (s.length() < len - 1) s = "0" + s;
        byte[] b = s.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(b, 0, dst, off, b.length);
        dst[off + len - 1] = 0;
    }
}
