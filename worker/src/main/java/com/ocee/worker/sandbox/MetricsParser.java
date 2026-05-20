package com.ocee.worker.sandbox;

public final class MetricsParser {
    public record Result(Long maxRssKib, Double userCpuSeconds, Double sysCpuSeconds, Double wallSeconds) {}

    public static final Result EMPTY = new Result(null, null, null, null);
    private MetricsParser() {}

    public static Result parse(String raw) {
        if (raw == null) return EMPTY;
        String line = raw.strip();
        if (line.isEmpty()) return EMPTY;
        String[] parts = line.split("\\s+");
        if (parts.length < 4) return EMPTY;
        return new Result(parseLong(parts[0]),
                parseDouble(parts[1]), parseDouble(parts[2]), parseDouble(parts[3]));
    }

    private static Long parseLong(String s) {
        try { return Long.parseLong(s); } catch (Exception e) { return null; }
    }
    private static Double parseDouble(String s) {
        try { return Double.parseDouble(s); } catch (Exception e) { return null; }
    }
}
