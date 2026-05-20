package com.ocee.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.Objects;

@Entity
@Table(name = "language")
@Getter @Setter @NoArgsConstructor @ToString
public class Language {

    @Id
    @Column(name = "id")
    private Integer id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "version")
    private String version;

    @Column(name = "source_file")
    private String sourceFile;

    @Column(name = "compile_command", columnDefinition = "TEXT")
    private String compileCommand;

    @Column(name = "run_command", nullable = false, columnDefinition = "TEXT")
    private String runCommand;

    @Column(name = "default_cpu_time", nullable = false)
    private Double defaultCpuTime;

    @Column(name = "default_memory", nullable = false)
    private Integer defaultMemory;

    @Column(name = "max_cpu_time", nullable = false)
    private Double maxCpuTime;

    @Column(name = "max_memory", nullable = false)
    private Integer maxMemory;

    @Column(name = "max_source_size", nullable = false)
    private Integer maxSourceSize;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @Column(name = "image", nullable = false)
    private String image;

    @Column(name = "compile_cpu_time", nullable = false)
    private Double compileCpuTime;

    @Column(name = "compile_memory", nullable = false)
    private Integer compileMemory;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Language other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }
}
