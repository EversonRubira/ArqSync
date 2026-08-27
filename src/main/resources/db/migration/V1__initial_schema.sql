CREATE TABLE projects (
    id              BIGINT PRIMARY KEY,
    path            VARCHAR(1024) NOT NULL,
    name            VARCHAR(255) NOT NULL,
    repository_url  VARCHAR(1024),
    created_at      TIMESTAMP NOT NULL,
    CONSTRAINT uq_projects_path UNIQUE (path)
);

CREATE SEQUENCE projects_id_seq START WITH 1 INCREMENT BY 50;

CREATE TABLE analyses (
    id                   BIGINT PRIMARY KEY,
    project_id           BIGINT NOT NULL REFERENCES projects (id),
    analyzed_at          TIMESTAMP NOT NULL,
    total_packages       INTEGER NOT NULL,
    total_classes        INTEGER NOT NULL,
    total_dependencies   INTEGER NOT NULL,
    cyclic_dependencies  INTEGER NOT NULL,
    violation_count      INTEGER NOT NULL
);

CREATE SEQUENCE analyses_id_seq START WITH 1 INCREMENT BY 50;
CREATE INDEX idx_analyses_project_id ON analyses (project_id);

CREATE TABLE package_metrics (
    id                     BIGINT PRIMARY KEY,
    analysis_id            BIGINT NOT NULL REFERENCES analyses (id),
    package_name           VARCHAR(512) NOT NULL,
    class_count            INTEGER NOT NULL,
    outgoing_dependencies  INTEGER NOT NULL,
    incoming_dependencies  INTEGER NOT NULL
);

CREATE SEQUENCE package_metrics_id_seq START WITH 1 INCREMENT BY 50;
CREATE INDEX idx_package_metrics_analysis_id ON package_metrics (analysis_id);

CREATE TABLE cycles (
    id            BIGINT PRIMARY KEY,
    analysis_id   BIGINT NOT NULL REFERENCES analyses (id),
    cycle_path    TEXT NOT NULL,
    length        INTEGER NOT NULL
);

CREATE SEQUENCE cycles_id_seq START WITH 1 INCREMENT BY 50;
CREATE INDEX idx_cycles_analysis_id ON cycles (analysis_id);
