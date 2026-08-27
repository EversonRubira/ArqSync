package com.arqsync.persistence;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * One scan's worth of analysis results, tied to a {@link Project}. History is
 * purely additive — a new scan always creates a new Analysis, never updates
 * an existing one (SPEC-persistence.md, 2.6).
 */
@Entity
@Table(name = "analyses")
public class Analysis {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "analysis_seq")
    @SequenceGenerator(name = "analysis_seq", sequenceName = "analyses_id_seq", allocationSize = 50)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(nullable = false)
    private LocalDateTime analyzedAt;

    @Column(nullable = false)
    private int totalPackages;

    @Column(nullable = false)
    private int totalClasses;

    @Column(nullable = false)
    private int totalDependencies;

    @Column(nullable = false)
    private int cyclicDependencies;

    @Column(nullable = false)
    private int violationCount;

    @OneToMany(mappedBy = "analysis", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PackageMetric> packageMetrics = new ArrayList<>();

    @OneToMany(mappedBy = "analysis", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Cycle> cycles = new ArrayList<>();

    protected Analysis() {
        // required by JPA
    }

    public Analysis(
            LocalDateTime analyzedAt,
            int totalPackages,
            int totalClasses,
            int totalDependencies,
            int cyclicDependencies,
            int violationCount
    ) {
        this.analyzedAt = analyzedAt;
        this.totalPackages = totalPackages;
        this.totalClasses = totalClasses;
        this.totalDependencies = totalDependencies;
        this.cyclicDependencies = cyclicDependencies;
        this.violationCount = violationCount;
    }

    public Long getId() {
        return id;
    }

    public Project getProject() {
        return project;
    }

    void setProject(Project project) {
        this.project = project;
    }

    public LocalDateTime getAnalyzedAt() {
        return analyzedAt;
    }

    public int getTotalPackages() {
        return totalPackages;
    }

    public int getTotalClasses() {
        return totalClasses;
    }

    public int getTotalDependencies() {
        return totalDependencies;
    }

    public int getCyclicDependencies() {
        return cyclicDependencies;
    }

    public int getViolationCount() {
        return violationCount;
    }

    public List<PackageMetric> getPackageMetrics() {
        return packageMetrics;
    }

    public void addPackageMetric(PackageMetric metric) {
        packageMetrics.add(metric);
        metric.setAnalysis(this);
    }

    public List<Cycle> getCycles() {
        return cycles;
    }

    public void addCycle(Cycle cycle) {
        cycles.add(cycle);
        cycle.setAnalysis(this);
    }
}
