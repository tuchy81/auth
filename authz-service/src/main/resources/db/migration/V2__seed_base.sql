-- =====================================================
-- V2: Base reference data (DML)
--   - 정적 기준데이터: 시스템, 샤딩 컨피그, 액션 마스터, 회사, 부서
--   - 모든 INSERT는 ON CONFLICT DO NOTHING — 멱등 적용
--   - 사용자/메뉴/API/권한 등 생성 데이터는 DataSeeder.java 가 담당
-- =====================================================

-- ----- Systems (3) -----
INSERT INTO TB_SYSTEM (system_cd, system_nm, system_nm_en, description,
                       owner_company_cd, system_type, base_url, frontend_type,
                       status, go_live_date)
VALUES
  ('ERP',    'ERP 시스템',  'ERP System',    'ERP 시스템 — 샘플 시드 데이터',  'CO01', 'ERP',    'https://erp.hd.local',    'VUE', 'A', DATE '2026-01-01'),
  ('MES',    'MES 시스템',  'MES System',    'MES 시스템 — 샘플 시드 데이터',  'CO01', 'MES',    'https://mes.hd.local',    'VUE', 'A', DATE '2026-01-01'),
  ('PORTAL', '포털 시스템', 'PORTAL System', '포털 시스템 — 샘플 시드 데이터', 'CO01', 'PORTAL', 'https://portal.hd.local', 'VUE', 'A', DATE '2026-01-01')
ON CONFLICT (system_cd) DO NOTHING;

-- ----- Shard configs -----
INSERT INTO TB_SYSTEM_SHARD_CONFIG (system_cd, shard_strategy, segment_position,
                                    segment_max_length, segment_fallback)
VALUES
  ('ERP',    'METHOD_DEPTH_SEG', 1, 32, '_root'),
  ('MES',    'METHOD_DEPTH',     1, 32, '_root'),
  ('PORTAL', 'METHOD_DEPTH_SEG', 1, 32, '_root')
ON CONFLICT (system_cd) DO NOTHING;

-- ----- Actions (R/C/U/D/A/X/P × 3 systems = 21 rows) -----
INSERT INTO TB_ACTION (system_cd, action_cd, action_nm, sort_order)
SELECT s.cd, a.cd, a.nm, a.so
  FROM (VALUES ('ERP'),('MES'),('PORTAL'))               AS s(cd)
  CROSS JOIN (VALUES
    ('R', '조회',     0),
    ('C', '생성',     1),
    ('U', '수정',     2),
    ('D', '삭제',     3),
    ('A', '승인',     4),
    ('X', '내보내기', 5),
    ('P', '출력',     6)
  ) AS a(cd, nm, so)
ON CONFLICT (system_cd, action_cd) DO NOTHING;

-- ----- Companies (10) -----
INSERT INTO TB_COMPANY (company_cd, company_nm) VALUES
  ('CO01', 'HD Company 1'),
  ('CO02', 'HD Company 2'),
  ('CO03', 'HD Company 3'),
  ('CO04', 'HD Company 4'),
  ('CO05', 'HD Company 5'),
  ('CO06', 'HD Company 6'),
  ('CO07', 'HD Company 7'),
  ('CO08', 'HD Company 8'),
  ('CO09', 'HD Company 9'),
  ('CO10', 'HD Company 10')
ON CONFLICT (company_cd) DO NOTHING;

-- ----- Departments (3 per company × 10 = 30) -----
INSERT INTO TB_DEPT (company_cd, dept_id, dept_cd, dept_nm)
SELECT c.cd,
       c.cd || '-D' || d,
       'D' || d,
       'Dept ' || d || ' of ' || c.cd
  FROM (VALUES ('CO01'),('CO02'),('CO03'),('CO04'),('CO05'),
               ('CO06'),('CO07'),('CO08'),('CO09'),('CO10')) AS c(cd)
  CROSS JOIN (VALUES (1),(2),(3)) AS d(d)
ON CONFLICT (company_cd, dept_id) DO NOTHING;
