package com.arqsync.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

@Entity
@Table(name = "package_metrics")
public class PackageMetric {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "package_metric_seq")
    @SequenceGenerator(name = "package_metric_seq", sequenceName = "package_metrics_id_seq", allocationSize = 50)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "analysis_id", nullable = false)
    private Analysis analysis;

    @Column(nullable = false)
    private String packageName;

    @Column(nullable = false)
    private int classCount;

    @Column(nullable = false)
    private int outgoingDependencies;

    @Column(nullable = false)
    private int incomingDependencies;

    protected PackageMetric() {
        // required by JPA
    }

    public PackageMetric(String packageName, int classCount, int outgoingDependencies, int incomingDependencies) {
        this.packageName = packageName;
        this.classCount = classCount;
        this.outgoingDependencies = outgoingDependencies;
        this.incomingDependencies = incomingDependencies;
    }

    public Long getId() {
        return id;
    }

    public Analysis getAnalysis() {
        return analysis;
    }

    void setAnalysis(Analysis analysis) {
        this.analysis = analysis;
    }

    public String getPackageName() {
        return packageName;
    }

    public int getClassCount() {
        return classCount;
    }

    public int getOutgoingDependencies() {
        return outgoingDependencies;
    }

    public int getIncomingDependencies() {
        return incomingDependencies;
    }
}
