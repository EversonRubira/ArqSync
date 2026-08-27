package com.arqsync.persistence;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * A scanned project, identified by its local filesystem path
 * (SPEC-persistence.md, 2.7).
 */
@Entity
@Table(name = "projects")
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "project_seq")
    @SequenceGenerator(name = "project_seq", sequenceName = "projects_id_seq", allocationSize = 50)
    private Long id;

    @Column(nullable = false, unique = true)
    private String path;

    @Column(nullable = false)
    private String name;

    @Column(name = "repository_url")
    private String repositoryUrl;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Analysis> analyses = new ArrayList<>();

    protected Project() {
        // required by JPA
    }

    public Project(String path, String name, String repositoryUrl, LocalDateTime createdAt) {
        this.path = path;
        this.name = name;
        this.repositoryUrl = repositoryUrl;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getPath() {
        return path;
    }

    public String getName() {
        return name;
    }

    public String getRepositoryUrl() {
        return repositoryUrl;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public List<Analysis> getAnalyses() {
        return analyses;
    }

    public void addAnalysis(Analysis analysis) {
        analyses.add(analysis);
        analysis.setProject(this);
    }
}
