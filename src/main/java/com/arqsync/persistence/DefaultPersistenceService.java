package com.arqsync.persistence;

import com.arqsync.analyzer.AnalysisResult;
import com.arqsync.scanner.ProjectScan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class DefaultPersistenceService implements PersistenceService {

    private static final Logger log = LoggerFactory.getLogger(DefaultPersistenceService.class);

    private final ProjectRepository projectRepository;
    private final AnalysisRepository analysisRepository;
    private final AnalysisResultMapper mapper;

    public DefaultPersistenceService(ProjectRepository projectRepository, AnalysisRepository analysisRepository) {
        this.projectRepository = projectRepository;
        this.analysisRepository = analysisRepository;
        this.mapper = new AnalysisResultMapper();
    }

    /**
     * Never throws (SPEC-persistence.md, 2.1). The transactional work happens in
     * {@link #persist}, kept as a separate method so the try/catch here sits
     * outside the transactional boundary — Spring's declarative rollback only
     * fires if the exception actually propagates out of the {@code @Transactional}
     * method (2.2).
     */
    @Override
    public void save(ProjectScan projectScan, AnalysisResult analysisResult) {
        try {
            persist(projectScan, analysisResult);
        } catch (Exception e) {
            log.error("Failed to persist analysis for project at '{}': {}",
                    projectScan.rootPath(), e.getMessage(), e);
        }
    }

    @Transactional
    void persist(ProjectScan projectScan, AnalysisResult analysisResult) {
        LocalDateTime now = LocalDateTime.now();

        Project project = projectRepository.findByPath(projectScan.rootPath())
                .orElseGet(() -> mapper.mapProject(projectScan, now));

        Analysis analysis = mapper.mapAnalysis(projectScan, analysisResult, now);
        project.addAnalysis(analysis);

        if (project.getId() == null) {
            projectRepository.save(project); // cascades to the new Analysis and its children
        } else {
            analysisRepository.save(analysis);
        }
    }
}
