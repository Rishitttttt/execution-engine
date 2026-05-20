package com.ocee.entity;

import com.ocee.common.Status;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "submission")
@Getter @Setter @NoArgsConstructor
public class Submission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "token", nullable = false, unique = true, updatable = false)
    private UUID token;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "language_id", nullable = false)
    private Language language;

    @Column(name = "source_code", nullable = false, columnDefinition = "TEXT")
    private String sourceCode;

    @Column(name = "std_in", columnDefinition = "TEXT")
    private String stdIn;

    @Column(name = "expected_output", columnDefinition = "TEXT")
    private String expectedOutput;

    @Column(name = "status", nullable = false)
    private Status status;

    @Column(name = "std_out", columnDefinition = "TEXT")
    private String stdOut;

    @Column(name = "std_err", columnDefinition = "TEXT")
    private String stdErr;

    @Column(name = "compile_output", columnDefinition = "TEXT")
    private String compileOutput;

    @Column(name = "message")
    private String message;

    @Column(name = "exit_code")
    private Integer exitCode;

    @Column(name = "exit_signal")
    private Integer exitSignal;

    @Column(name = "time")
    private Double time;

    @Column(name = "wall_time")
    private Double wallTime;

    @Column(name = "memory")
    private Integer memory;

    @Column(name = "execution_host")
    private String executionHost;

    @Column(name = "cpu_time_limit")
    private Double cpuTimeLimit;

    @Column(name = "cpu_extra_time_limit")
    private Double cpuExtraTimeLimit;

    @Column(name = "wall_time_limit")
    private Double wallTimeLimit;

    @Column(name = "memory_limit")
    private Integer memoryLimit;

    @Column(name = "stack_limit")
    private Integer stackLimit;

    @Column(name = "max_process_and_or_thread_limit")
    private Integer maxProcessesAndOrThreadsLimit;

    @Column(name = "max_file_size_limit")
    private Integer maxFileSizeLimit;

    @Column(name = "idempotency_key", unique = true, updatable = false)
    private UUID idempotencyKey;

    @Column(name = "idempotency_body_sha256")
    private byte[] idempotencyBodySha256;

    @Column(name = "callback_url")
    private String callbackUrl;

    @Column(name = "created_at", insertable = false, updatable = false)
    @Generated(event = EventType.INSERT)
    private Instant createdAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Submission other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }
}
