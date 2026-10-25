-- Synapse core schema: users, analysed projects, knowledge graph and generated documents.

CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    name          VARCHAR(120) NOT NULL,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE projects (
    id             BIGSERIAL PRIMARY KEY,
    owner_id       BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name           VARCHAR(200) NOT NULL,
    source_type    VARCHAR(16)  NOT NULL,           -- GITHUB | ZIP
    source_url     VARCHAR(500),
    branch         VARCHAR(200),
    depth          VARCHAR(16)  NOT NULL DEFAULT 'STANDARD',
    status         VARCHAR(32)  NOT NULL,
    progress       INT          NOT NULL DEFAULT 0,
    status_message VARCHAR(500),
    ai_available   BOOLEAN,
    stats          JSONB        NOT NULL DEFAULT '{}'::jsonb,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    completed_at   TIMESTAMPTZ
);

CREATE INDEX idx_projects_owner ON projects (owner_id, created_at DESC);

-- Graph nodes are numbered per project so edges stay compact.
CREATE TABLE graph_nodes (
    project_id     BIGINT       NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    id             INT          NOT NULL,
    kind           VARCHAR(24)  NOT NULL,           -- REPOSITORY, DIRECTORY, FILE, CLASS, FUNCTION, METHOD, ENDPOINT, EXTERNAL
    subkind        VARCHAR(32),                     -- interface, struct, enum, ... / http method
    name           VARCHAR(400) NOT NULL,
    qualified_name VARCHAR(1000) NOT NULL,
    language       VARCHAR(32),
    file_path      VARCHAR(1000),
    start_line     INT,
    end_line       INT,
    signature      TEXT,
    snippet        TEXT,
    summary        TEXT,
    search         TSVECTOR GENERATED ALWAYS AS (
                       setweight(to_tsvector('simple', coalesce(name, '')), 'A') ||
                       setweight(to_tsvector('simple', regexp_replace(coalesce(qualified_name, ''), '[./:\\#]', ' ', 'g')), 'B') ||
                       setweight(to_tsvector('english', coalesce(summary, '')), 'C')
                   ) STORED,
    PRIMARY KEY (project_id, id)
);

CREATE INDEX idx_nodes_kind   ON graph_nodes (project_id, kind);
CREATE INDEX idx_nodes_search ON graph_nodes USING GIN (search);
CREATE INDEX idx_nodes_name   ON graph_nodes (project_id, lower(name));

CREATE TABLE graph_edges (
    project_id BIGINT      NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    source_id  INT         NOT NULL,
    target_id  INT         NOT NULL,
    kind       VARCHAR(24) NOT NULL,                -- CONTAINS, IMPORTS, CALLS, EXTENDS, IMPLEMENTS, DEPENDS_ON, HANDLES, INSTANTIATES
    weight     INT         NOT NULL DEFAULT 1,
    PRIMARY KEY (project_id, source_id, target_id, kind)
);

CREATE INDEX idx_edges_target ON graph_edges (project_id, target_id);

CREATE TABLE documents (
    id         BIGSERIAL PRIMARY KEY,
    project_id BIGINT       NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    kind       VARCHAR(32)  NOT NULL,               -- README, ARCHITECTURE, BUSINESS_LOGIC, API
    title      VARCHAR(200) NOT NULL,
    file_name  VARCHAR(200) NOT NULL,
    content    TEXT         NOT NULL,
    ai_generated BOOLEAN    NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (project_id, kind)
);
