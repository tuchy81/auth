# HD Authz Platform — MVP

`authz_design.md` v2.3 기반 엔터프라이즈 권한관리/인가 플랫폼 MVP 구현체.

## 구성

| 모듈 | 포트 | 설명 |
|---|---|---|
| `authz-service` | 8080 | Spring Boot 인가/권한관리 서비스 (PG + Caffeine + Redis 3계층) |
| `sample-api`    | 8081 | 1,000개 엔드포인트를 가진 샘플 백엔드, 모든 요청에서 authz-service 호출 |
| `authz-admin`   | 5173 | Vue3 + Element Plus 관리 UI |
| `postgres`      | 5432 | Source of Truth |
| `redis`         | 6379 | L2 cache + Pub/Sub |
| `pgadmin`       | 5050 | DB 관리 (admin@example.com / admin) |

## 1회만 실행

```bash
# 1) 인프라 컨테이너
docker compose up -d postgres redis pgadmin

# 2) 백엔드 (각각 별도 터미널)
cd authz-service && mvn spring-boot:run         # 1000 APIs + sample data 자동 시드
cd sample-api    && mvn spring-boot:run

# 3) 프론트
cd authz-admin && npm install && npm run dev
```

브라우저: http://localhost:5173

## 동작 검증

```bash
# 인가 체크 (allow=true)
curl 'http://localhost:8080/api/v1/authz/check?system_cd=ERP&company_cd=CO01&dept_id=CO01-D1&user_id=U00001&method=GET&api_url=/api/purchase/items/n0'

# 메뉴 트리 (사용자 권한 적용)
curl -X POST http://localhost:8080/api/v1/authz/menu-tree \
  -H 'Content-Type: application/json' \
  -d '{"system_cd":"ERP","company_cd":"CO01","dept_id":"CO01-D1","user_id":"U00001"}'

# Warm-up
curl -X POST http://localhost:8080/api/v1/authz/warmup/system \
  -H 'Content-Type: application/json' \
  -d '{"system_cd":"ERP","user_id":"U00001"}'

# 시뮬레이션
curl -X POST http://localhost:8080/api/v1/authz/simulate/grant \
  -H 'Content-Type: application/json' \
  -d '{"system_cd":"ERP","subject_type":"C","subject_id":"CO01","target_id":1,"action_cd":"R"}'

# 감사
curl 'http://localhost:8080/api/v1/audit/changes?system_cd=ERP&size=20'

# 캐시 통계
curl http://localhost:8080/api/v1/authz/cache/stats

# Sample API (인가 통과 시 200, 거부 시 403)
curl -H 'X-User-Id: U00001' http://localhost:8081/api/purchase/items/n0
```

## 스펙 매핑

| 스펙 §  | 구현 위치 |
|---|---|
| §5 데이터 모델 | `authz-service/src/main/resources/db/migration/V1__core_schema.sql` |
| §6 캐시·샤딩 | `cache/ShardKeyBuilder.java`, `cache/PermCacheService.java` (METHOD_DEPTH + METHOD_DEPTH_SEG) |
| §7 인가 조회 API | `api/AuthzController.java` |
| §8 변경/동기화 | `service/PermissionService.java` (Outbox emit) + `sync/SyncWorker.java` (Polling) |
| §9 Warm-up | `service/WarmupService.java`, `api/WarmupController.java` |
| §10.6 시뮬레이션 | `service/SimulationService.java`, `api/SimulationController.java` |
| §10.5 감사 | `api/AuditController.java`, `domain/AuditLog.java` |

## 시드 데이터

- 시스템 3개: `ERP` (METHOD_DEPTH_SEG), `MES` (METHOD_DEPTH), `PORTAL` (METHOD_DEPTH_SEG)
- 회사 10개 (`CO01..CO10`) × 부서 3개 = 30개 부서
- 사용자 200명 (`U00001..U00200`)
- 메뉴 ~92개 (Folder + Leaf), MenuImpl + MenuAction 매핑 포함
- API **1,000개** (3 시스템 분배), 메뉴-액션-API 매핑
- 권한 ~870건 (C/D/U 혼합)

부팅 직후 `SyncWorker`가 모든 사용자에 대해 Redis Set을 빌드하고, Caffeine L1으로도 워밍됩니다.

## 미포함 (v2)

Kafka, Debezium CDC, Keycloak/BFF 통합, 모바일 라우트.
