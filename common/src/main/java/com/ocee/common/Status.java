package com.ocee.common;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public enum Status {
    QUEUED(1, "In Queue"),
    PROCESS(2, "Processing"),
    AC(3, "Accepted"),
    WA(4, "Wrong answer"),
    TLE(5, "Time limit exceeded"),
    CE(6, "Compilation error"),
    SIGSEGV(7, "Runtime Error (SIGSEGV)"),
    SIGXFSZ(8, "Runtime Error (SIGXFSZ)"),
    SIGFPE(9, "Runtime Error (SIGFPE)"),
    SIGABRT(10, "Runtime Error (SIGABRT)"),
    NZEC(11, "Runtime Error (NZEC)"),
    OTHER(12, "Runtime Error (Other)"),
    BOXERR(13, "Internal Error"),
    EXEERR(14, "Exec Format Error"),
    MLE(15, "Memory limit exceeded");

    private static final Map<Integer, Status> BY_CODE =
            Stream.of(values()).collect(Collectors.toUnmodifiableMap(Status::getCode, Function.identity()));

    private final int code;
    private final String description;

    Status(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public int getCode() { return code; }
    public String getDescription() { return description; }

    public static Status fromCode(int code) {
        Status s = BY_CODE.get(code);
        if (s == null) throw new IllegalArgumentException("Unknown status code: " + code);
        return s;
    }

    public static Status findRuntimeErrorBySignal(Integer exitSignal) {
        if (exitSignal == null) return OTHER;
        return switch (exitSignal) {
            case 11 -> SIGSEGV;
            case 25 -> SIGXFSZ;
            case 8  -> SIGFPE;
            case 6  -> SIGABRT;
            default -> OTHER;
        };
    }
}
