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

/**
 * A persisted dependency cycle. Distinct from {@link com.arqsync.analyzer.Cycle}
 * (the in-memory Analyzer model) — this is the JPA row it gets mapped to.
 */
@Entity
@Table(name = "cycles")
public class Cycle {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "cycle_seq")
    @SequenceGenerator(name = "cycle_seq", sequenceName = "cycles_id_seq", allocationSize = 50)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "analysis_id", nullable = false)
    private Analysis analysis;

    @Column(nullable = false, columnDefinition = "text")
    private String cyclePath;

    @Column(nullable = false)
    private int length;

    protected Cycle() {
        // required by JPA
    }

    public Cycle(String cyclePath, int length) {
        this.cyclePath = cyclePath;
        this.length = length;
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

    public String getCyclePath() {
        return cyclePath;
    }

    public int getLength() {
        return length;
    }
}
