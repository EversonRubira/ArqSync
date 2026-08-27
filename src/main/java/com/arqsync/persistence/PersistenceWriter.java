package com.arqsync.persistence;

import com.arqsync.analyzer.AnalysisResult;
import com.arqsync.scanner.ProjectScan;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * The actual transactional write (SPEC-persistence.md, 2.2/2.4/2.5), split
 * out into its own bean so {@code @Transactional} genuinely applies.
 *
 * <p>The Spec described this as a private-ish method in the same class as
 * {@link DefaultPersistenceService#save}, with {@code save} calling it via
 * plain {@code this.persist(...)}. That doesn't actually work: Spring's
 * {@code @Transactional} is implemented via a proxy around the bean, and a
 * same-class self-invocation (`this.persist(...)`) bypasses that proxy
 * entirely — the classic Spring AOP self-invocation pitfall. The symptom
 * only shows up for an *existing* Project (a real, Hibernate-managed lazy
 * collection): {@code LazyInitializationException} when
 * {@code project.addAnalysis(...)} touches it outside of a real transaction.
 * It went unnoticed in {@code DefaultPersistenceServiceTest} because
 * {@code @DataJpaTest} wraps each test method in its own ambient
 * transaction, which happens to keep a Session open regardless — masking
 * the bug in tests while it still breaks in a real run. Found by actually
 * running the packaged jar against a real, already-populated Postgres.
 */
class PersistenceWriter {

    private final ProjectRepository projectRepository;
    private final AnalysisRepository analysisRepository;
    private final AnalysisResultMapper mapper;

    PersistenceWriter(ProjectRepository projectRepository, AnalysisRepository analysisRepository) {
        this.projectRepository = projectRepository;
        this.analysisRepository = analysisRepository;
        this.mapper = new AnalysisResultMapper();
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
