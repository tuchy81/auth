# Spec Coverage Report (3-round 갭 분석 결과)

`authz_design.md` v2.3 (1,673 lines + 47-page PDF) 대비 현재 구현 커버리지.

## 통계
- Java 파일: **83개**
- Vue 화면/컴포넌트: **17개** (총 2,533 줄)
- DB 테이블: **21개** (스펙 §5 정의 전체)
- API 엔드포인트: **70+개**

## 스펙 섹션별 커버리지

| 섹션 | 항목 | 상태 |
|---|---|---|
| §1-§4 | 설계 철학, 용어, 아키텍처 | ✓ 구현됨 |
| §5.1 | TB_SYSTEM + TB_SYSTEM_ATTR | ✓ CRUD 풀 구현 (UI 6탭) |
| §5.2 | TB_COMPANY/DEPT/USER | ✓ CRUD |
| §5.3 | TB_MENU 트리 (F/M/L) | ✓ CRUD + 트리 UI |
| §5.4 | TB_MENU_IMPL (구현체 메타) | ✓ CRUD + JSON 편집 |
| §5.5 | TB_ACTION/MENU_ACTION/API/MENU_ACTION_API | ✓ CRUD + 매핑 다이얼로그 |
| §5.6 | TB_SYSTEM_SHARD_CONFIG | ✓ CRUD + 영향 미리보기 |
| §5.7 | TB_COMPANY_GROUP / DEPT_GROUP / USER_GROUP + maps | ✓ CRUD + CSV import |
| §5.8 | TB_PERMISSION | ✓ CRUD + 일괄부여 |
| §5.9 | TB_PERM_CHANGE_LOG (Outbox) | ✓ Polling worker로 처리 |
| - | TB_AUDIT_LOG (감사) | ✓ 자동 기록 + 4유형 조회 |
| §6.1-§6.7 | 캐시 샤딩, L1/L2 키 통일, TTL, Caffeine 설정 | ✓ ShardKeyBuilder + PermCacheService |
| §6.2.1 | METHOD_DEPTH 전략 | ✓ |
| §6.2.2 | METHOD_DEPTH_SEG 전략 | ✓ |
| §6.2.3 | SEG 추출 알고리즘 | ✓ ShardKeyBuilder.extractSeg |
| §7.1.1 | GET /authz/check | ✓ |
| §7.1.2 | GET /authz/menu-actions | ✓ |
| §7.1.3 | POST /authz/menu-tree (가지치기) | ✓ |
| §7.1.4 | POST /authz/check-batch | ✓ |
| §7.4 | Negative 캐시 | ✓ PermCacheService |
| §8.1 | 변경 이벤트 emit (10가지) | ✓ MENU_TREE_CHANGE/MENU_ACTION_API_CHANGE/SHARD_STRATEGY_CHANGE 등 |
| §8.3 | 폴더 권한 → 자손 리프 전개 | ✓ PermissionFlattener |
| §8.5 | Set rebuild | ✓ Pipeline |
| §8.7 | Caffeine 무효화 | ✓ Pub/Sub |
| §9.1-§9.3 | Warm-up API (system/menu) | ✓ WarmupController |
| §10.1 | 전체 메뉴 구조 (5탭) | ✓ Vue 사이드바 |
| §10.2 | 권한부여 화면 (3-pane, 6 주체, 출처 배지) | ✓ GrantView |
| §10.3 | 일괄부여 모달 (4단계 + 영향) | ✓ BulkGrantDialog |
| §10.4 | 그룹관리 화면 (CG/DG/UG 탭) | ✓ GroupsView |
| §10.5 | 권한조회/감사 (3가지 + 매트릭스) | ✓ AuditView |
| §10.6 | 시뮬레이션 (폴더 자손 미리보기) | ✓ SimulateView |
| §10.7 | 시스템 마스터 (좌측 목록 + 우측 6탭) | ✓ SystemsView |
| §10.8 | 메뉴 마스터 (트리 + 4탭 + 신규 다이얼로그) | ✓ MenusView |
| §10.8.2 | API 매핑 다이얼로그 (자동 추천) | ✓ MenusView 다이얼로그 |
| §10.9 | API 마스터 (통계 바 + 필터) | ✓ ApisView |
| §10.10 | 샤딩 컨피그 (샘플 미리보기 + 영향) | ✓ ShardView |
| §11 | 성능 모니터링 지표 | 부분 (cache stats 노출) |
| §12 | 운영/감사 (ITGC) | 부분 (audit log 적재) |

## 라운드별 보완

### Round 1 (R1) — 마스터 CRUD + 핵심 UI
**Backend:** SystemAttr/Group 엔티티 추가, MasterController 재작성 (PUT/DELETE), MenuController 신규 (Menu/MenuImpl/MenuAction/MenuActionApi 풀 CRUD), MenuMappingService (자동 추천), StatsService (시스템별 통계)
**UI:** Pinia 스토어 + 헤더 시스템 셀렉터, SystemsView 6탭, MenusView 트리+4탭+신규다이얼로그+API매핑다이얼로그, ApisView 통계바+필터, ActionsView CRUD

### Round 2 (R2) — 권한부여·그룹
**Backend:** EffectivePermService (출처 분해 — 직접/UG/부서/회사/폴더), BulkGrantService (영향 사전계산), GroupController (CG/DG/UG + CSV import), EffectiveAndBulkController
**UI:** GrantView 6주체 + 출처 색상 배지 + 출처 상세, BulkGrantDialog 4단계, GroupsView 풀구현 + CSV import

### Round 3 (R3) — 조회·시뮬레이션·샤딩
**Backend:** AuditController.byApi (API → 주체 역추적), ShardPreviewService/Controller (영향 사전계산)
**UI:** AuditView 4유형 조회 + 매트릭스 + 출처 배지 + CSV export, SimulateView 폴더 자손 미리보기, ShardView 영향 미리보기 + 샘플 URL 결과

## 잔여 갭 (v2 단계로 보류)

- **§4.1 메시징**: Kafka, Debezium CDC — Outbox + Polling으로 대체
- **§9.4 BFF 통합**: Keycloak/OIDC 연동
- **§10.8.3 일괄 매핑 도구** (수백 메뉴 자동 매칭) — 추천 API는 다이얼로그에 있으나 시스템 전체 일괄 적용 UI 없음
- **§10.9 OpenAPI 가져오기 / 스캔 동기화** — API 등록 다이얼로그까지 (자동 import 미구현)
- **§11 부하 테스트 시나리오** (별도 도구)
- **§12.4 분기 권한 재검토 워크플로우**
- **§12.6 Vue router/index.js ↔ TB_MENU_IMPL CI 자동 동기화**
- **§13.3 Kong/webMethods/Prometheus/Grafana** 운영 인프라
- 모바일 라우트(`mobile_route_path`) 활용은 모달에서 입력만 가능 (실제 모바일 BFF 없음)

## 디렉토리 구조

```
authz-service/  (Spring Boot, 83 java)
├── api/         13 컨트롤러
├── cache/       3 (ShardKeyBuilder, PermCacheService, InvalidationListener)
├── common/      UrlUtils
├── config/      Cors / Redis / Caffeine
├── domain/      27 엔티티 + Id 클래스
├── repo/        16 리포
├── seed/        DataSeeder (1000 APIs)
├── service/     8 (Authz/Permission/Flattener/Warmup/Simulation/Effective/Bulk/Stats/Mapping/ShardPreview)
└── sync/        SyncWorker (Outbox polling)

authz-admin/    (Vue3, 17 vue)
├── api/         REST 클라이언트
├── store/       Pinia (시스템 컨텍스트)
├── router/
└── views/
    ├── GrantView, GroupsView, AuditView, SimulateView, MenuTreeView
    ├── components/BulkGrantDialog
    └── master/  SystemsView, MenusView, ApisView, ActionsView, ShardView

sample-api/     (Spring Boot, 1000 endpoints)
└── filter/AuthzFilter — 모든 요청에 authz check
```
