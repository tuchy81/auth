-- =====================================================
-- HD Authz Platform — Core Schema (spec §5)
-- =====================================================

-- ---------- 5.1 SYSTEM ----------
CREATE TABLE TB_SYSTEM (
  system_cd        VARCHAR(20)  PRIMARY KEY,
  system_nm        VARCHAR(100) NOT NULL,
  system_nm_en     VARCHAR(100),
  description      VARCHAR(500),
  owner_company_cd VARCHAR(10),
  owner_division   VARCHAR(50),
  owner_dept_id    VARCHAR(50),
  owner_user_id    VARCHAR(50),
  system_type      VARCHAR(20),
  system_category  VARCHAR(50),
  base_url         VARCHAR(500),
  frontend_type    VARCHAR(20),
  status           VARCHAR(1)   DEFAULT 'A',
  go_live_date     DATE,
  end_of_life_date DATE,
  created_at       TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
  updated_at       TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
  updated_by       VARCHAR(50)
);
CREATE INDEX idx_system_owner_company ON TB_SYSTEM(owner_company_cd);
CREATE INDEX idx_system_status        ON TB_SYSTEM(status);

CREATE TABLE TB_SYSTEM_ATTR (
  system_cd  VARCHAR(20),
  attr_key   VARCHAR(50),
  attr_value VARCHAR(500),
  PRIMARY KEY (system_cd, attr_key)
);

CREATE TABLE TB_SYSTEM_SHARD_CONFIG (
  system_cd          VARCHAR(20) PRIMARY KEY,
  shard_strategy     VARCHAR(20) NOT NULL,
  segment_position   INT,
  segment_max_length INT DEFAULT 32,
  segment_fallback   VARCHAR(50) DEFAULT '_root',
  updated_at         TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_by         VARCHAR(50)
);

-- ---------- 5.2 ORG ----------
CREATE TABLE TB_COMPANY (
  company_cd VARCHAR(10) PRIMARY KEY,
  company_nm VARCHAR(200)
);

CREATE TABLE TB_DEPT (
  company_cd VARCHAR(10),
  dept_id    VARCHAR(50),
  dept_cd    VARCHAR(50),
  dept_nm    VARCHAR(200),
  PRIMARY KEY (company_cd, dept_id)
);

CREATE TABLE TB_USER (
  user_id    VARCHAR(50) PRIMARY KEY,
  company_cd VARCHAR(10) NOT NULL,
  dept_id    VARCHAR(50) NOT NULL,
  user_nm    VARCHAR(100),
  email      VARCHAR(200),
  status     VARCHAR(1) DEFAULT 'A'
);
CREATE INDEX idx_user_dept ON TB_USER(company_cd, dept_id);

-- ---------- 5.3 MENU TREE ----------
CREATE TABLE TB_MENU (
  menu_id        BIGSERIAL PRIMARY KEY,
  system_cd      VARCHAR(20)  NOT NULL,
  parent_menu_id BIGINT,
  menu_type      VARCHAR(1)   NOT NULL,            -- F/M/L
  menu_cd        VARCHAR(50),
  menu_nm        VARCHAR(200) NOT NULL,
  menu_nm_en     VARCHAR(200),
  menu_desc      VARCHAR(500),
  icon           VARCHAR(50),
  sort_order     INT          DEFAULT 0,
  is_visible     VARCHAR(1)   DEFAULT 'Y',
  is_default     VARCHAR(1)   DEFAULT 'N',
  status         VARCHAR(1)   DEFAULT 'A',
  effective_from DATE,
  effective_to   DATE,
  created_by     VARCHAR(50),
  created_at     TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
  updated_by     VARCHAR(50),
  updated_at     TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_menu_parent ON TB_MENU(parent_menu_id);
CREATE INDEX idx_menu_system ON TB_MENU(system_cd, status);
CREATE INDEX idx_menu_type   ON TB_MENU(system_cd, menu_type);
CREATE UNIQUE INDEX uk_menu_cd ON TB_MENU(system_cd, menu_cd) WHERE menu_cd IS NOT NULL;

CREATE TABLE TB_MENU_IMPL (
  menu_id           BIGINT PRIMARY KEY,
  route_path        VARCHAR(500),
  route_name        VARCHAR(100),
  component_name    VARCHAR(200),
  component_path    VARCHAR(500),
  route_meta        JSONB,
  route_params      JSONB,
  route_query       JSONB,
  external_url      VARCHAR(1000),
  open_target       VARCHAR(20),
  has_layout        VARCHAR(1) DEFAULT 'Y',
  is_full_screen    VARCHAR(1) DEFAULT 'N',
  is_modal          VARCHAR(1) DEFAULT 'N',
  mobile_supported  VARCHAR(1) DEFAULT 'Y',
  mobile_route_path VARCHAR(500),
  updated_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_by        VARCHAR(50)
);

-- ---------- 5.5 ACTION / API ----------
CREATE TABLE TB_ACTION (
  system_cd  VARCHAR(20),
  action_cd  VARCHAR(10),
  action_nm  VARCHAR(50),
  sort_order INT,
  PRIMARY KEY (system_cd, action_cd)
);

CREATE TABLE TB_MENU_ACTION (
  menu_id   BIGINT,
  action_cd VARCHAR(10),
  PRIMARY KEY (menu_id, action_cd)
);

CREATE TABLE TB_API (
  api_id      BIGSERIAL PRIMARY KEY,
  system_cd   VARCHAR(20)  NOT NULL,
  http_method VARCHAR(10)  NOT NULL,
  url_pattern VARCHAR(500) NOT NULL,
  url_depth   INT          NOT NULL,
  shard_seg   VARCHAR(64),
  service_nm  VARCHAR(100),
  description VARCHAR(500),
  status      VARCHAR(1) DEFAULT 'A'
);
CREATE UNIQUE INDEX uk_api_route ON TB_API(system_cd, http_method, url_pattern);
CREATE INDEX idx_api_shard ON TB_API(system_cd, http_method, url_depth, shard_seg);

CREATE TABLE TB_MENU_ACTION_API (
  menu_id   BIGINT,
  action_cd VARCHAR(10),
  api_id    BIGINT,
  PRIMARY KEY (menu_id, action_cd, api_id)
);
CREATE INDEX idx_maa_api ON TB_MENU_ACTION_API(api_id);

-- ---------- 5.7 GROUPS ----------
CREATE TABLE TB_COMPANY_GROUP (
  company_group_id BIGSERIAL PRIMARY KEY,
  group_nm   VARCHAR(100),
  group_type VARCHAR(20)
);
CREATE TABLE TB_COMPANY_GROUP_MAP (
  company_group_id BIGINT,
  company_cd       VARCHAR(10),
  PRIMARY KEY (company_group_id, company_cd)
);

CREATE TABLE TB_DEPT_GROUP (
  dept_group_id BIGSERIAL PRIMARY KEY,
  company_cd    VARCHAR(10),
  group_nm      VARCHAR(100)
);
CREATE TABLE TB_DEPT_GROUP_MAP (
  dept_group_id BIGINT,
  company_cd    VARCHAR(10),
  dept_id       VARCHAR(50),
  PRIMARY KEY (dept_group_id, company_cd, dept_id)
);
CREATE INDEX idx_dgmap_dept ON TB_DEPT_GROUP_MAP(company_cd, dept_id);

CREATE TABLE TB_USER_GROUP (
  user_group_id BIGSERIAL PRIMARY KEY,
  company_cd    VARCHAR(10),
  group_nm      VARCHAR(100),
  group_type    VARCHAR(20)
);
CREATE TABLE TB_USER_GROUP_MAP (
  user_group_id BIGINT,
  user_id       VARCHAR(50),
  PRIMARY KEY (user_group_id, user_id)
);
CREATE INDEX idx_ugmap_user ON TB_USER_GROUP_MAP(user_id);

-- ---------- 5.8 PERMISSION ----------
CREATE TABLE TB_PERMISSION (
  perm_id      BIGSERIAL PRIMARY KEY,
  system_cd    VARCHAR(20) NOT NULL,
  company_cd   VARCHAR(10) NOT NULL,
  subject_type VARCHAR(2)  NOT NULL,
  subject_id   VARCHAR(50) NOT NULL,
  target_type  VARCHAR(1)  NOT NULL,
  target_id    BIGINT      NOT NULL,
  action_cd    VARCHAR(10) NOT NULL,
  valid_from   TIMESTAMP,
  valid_to     TIMESTAMP,
  created_by   VARCHAR(50),
  created_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_by   VARCHAR(50),
  updated_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX uk_perm ON TB_PERMISSION
  (system_cd, company_cd, subject_type, subject_id, target_type, target_id, action_cd);
CREATE INDEX idx_perm_subject ON TB_PERMISSION(system_cd, subject_type, subject_id);
CREATE INDEX idx_perm_target  ON TB_PERMISSION(target_type, target_id);

-- ---------- 5.9 OUTBOX ----------
CREATE TABLE TB_PERM_CHANGE_LOG (
  seq          BIGSERIAL PRIMARY KEY,
  event_type   VARCHAR(30),
  scope_type   VARCHAR(2),
  scope_id     VARCHAR(50),
  system_cd    VARCHAR(20),
  payload      JSONB,
  processed_yn VARCHAR(1) DEFAULT 'N',
  created_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  processed_at TIMESTAMP
);
CREATE INDEX idx_log_unproc ON TB_PERM_CHANGE_LOG(processed_yn, seq) WHERE processed_yn = 'N';

-- ---------- AUDIT ----------
CREATE TABLE TB_AUDIT_LOG (
  audit_id     BIGSERIAL PRIMARY KEY,
  occurred_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  actor_id     VARCHAR(50),
  action       VARCHAR(40)  NOT NULL,           -- GRANT/REVOKE/MENU_CREATE/SIM_RUN/...
  system_cd    VARCHAR(20),
  subject_type VARCHAR(2),
  subject_id   VARCHAR(50),
  target_type  VARCHAR(1),
  target_id    BIGINT,
  action_cd    VARCHAR(10),
  detail       JSONB
);
CREATE INDEX idx_audit_subject ON TB_AUDIT_LOG(system_cd, subject_type, subject_id);
CREATE INDEX idx_audit_target  ON TB_AUDIT_LOG(target_type, target_id);
CREATE INDEX idx_audit_time    ON TB_AUDIT_LOG(occurred_at DESC);
