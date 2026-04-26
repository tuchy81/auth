package com.hd.authz.seed;

import com.hd.authz.cache.ShardKeyBuilder;
import com.hd.authz.common.UrlUtils;
import com.hd.authz.domain.*;
import com.hd.authz.repo.*;
import com.hd.authz.sync.SyncWorker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

@Slf4j
@Component
@Order(10)
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    @Value("${authz.seed.enabled:true}")
    private boolean enabled;

    @Value("${authz.seed.api-count:1000}")
    private int apiCount;

    private final SystemRepo systemRepo;
    private final ShardConfigRepo shardConfigRepo;
    private final CompanyRepo companyRepo;
    private final DeptRepo deptRepo;
    private final UserRepo userRepo;
    private final ActionRepo actionRepo;
    private final MenuRepo menuRepo;
    private final MenuImplRepo menuImplRepo;
    private final MenuActionRepo menuActionRepo;
    private final ApiRepo apiRepo;
    private final MenuActionApiRepo menuActionApiRepo;
    private final PermissionRepo permissionRepo;
    private final SyncWorker syncWorker;

    private static final String[] SYSTEMS = {"ERP", "MES", "PORTAL"};
    private static final String[] DOMAINS_ERP = {"purchase", "order", "invoice", "stock", "vendor", "contract", "approval"};
    private static final String[] DOMAINS_MES = {"workorder", "production", "quality", "equipment", "material", "shift"};
    private static final String[] DOMAINS_PORTAL = {"notice", "board", "doc", "schedule", "user", "report", "search"};
    private static final String[] METHODS = {"GET", "POST", "PUT", "DELETE", "PATCH"};
    private static final String[] ACTIONS = {"R", "C", "U", "D", "A", "X", "P"};

    @Override
    @Transactional
    public void run(String... args) {
        if (!enabled) return;
        if (systemRepo.count() == 0) {
            log.info("=== Seeding sample data: 3 systems / 10 companies / ~30 depts / ~200 users / {} APIs ===", apiCount);
            seedCompanies();
            seedSystems();
            seedActions();
            seedUsers();
            Map<String, List<Menu>> menuLeavesBySystem = seedMenusAndApis();
            seedPermissions(menuLeavesBySystem);
        } else {
            log.info("Seed skipped — already populated; will rebuild caches");
        }

        log.info("Triggering full cache rebuild for all systems / users…");
        for (String s : SYSTEMS) {
            syncWorker.rebuildAllUsers(s);
        }
        log.info("=== Seed/rebuild complete ===");
    }

    private void seedCompanies() {
        for (int i = 1; i <= 10; i++) {
            String cd = String.format("CO%02d", i);
            companyRepo.save(Company.builder()
                    .companyCd(cd).companyNm("HD Company " + i).build());
            // 3 depts per company
            for (int d = 1; d <= 3; d++) {
                String deptId = cd + "-D" + d;
                deptRepo.save(Dept.builder()
                        .companyCd(cd).deptId(deptId).deptCd("D" + d)
                        .deptNm("Dept " + d + " of " + cd).build());
            }
        }
    }

    private void seedSystems() {
        String[] names = {"ERP 시스템", "MES 시스템", "포털 시스템"};
        String[] types = {"ERP", "MES", "PORTAL"};
        String[] strats = {"METHOD_DEPTH_SEG", "METHOD_DEPTH", "METHOD_DEPTH_SEG"};
        for (int i = 0; i < SYSTEMS.length; i++) {
            String s = SYSTEMS[i];
            systemRepo.save(SystemEntity.builder()
                    .systemCd(s).systemNm(names[i]).systemNmEn(s + " System")
                    .description(names[i] + " — sample seed data")
                    .ownerCompanyCd("CO01").systemType(types[i])
                    .baseUrl("https://" + s.toLowerCase() + ".hd.local")
                    .frontendType("VUE").status("A").goLiveDate(LocalDate.of(2026, 1, 1))
                    .build());
            shardConfigRepo.save(SystemShardConfig.builder()
                    .systemCd(s).shardStrategy(strats[i])
                    .segmentPosition(1).segmentMaxLength(32).segmentFallback("_root")
                    .build());
        }
    }

    private void seedActions() {
        String[] names = {"조회","생성","수정","삭제","승인","내보내기","출력"};
        for (String s : SYSTEMS) {
            for (int i = 0; i < ACTIONS.length; i++) {
                actionRepo.save(Action.builder()
                        .systemCd(s).actionCd(ACTIONS[i])
                        .actionNm(names[i]).sortOrder(i).build());
            }
        }
    }

    private void seedUsers() {
        Random rnd = new Random(42);
        // ~200 users distributed across 30 depts
        List<Dept> depts = deptRepo.findAll();
        for (int i = 1; i <= 200; i++) {
            Dept d = depts.get(rnd.nextInt(depts.size()));
            String uid = String.format("U%05d", i);
            userRepo.save(UserEntity.builder()
                    .userId(uid).companyCd(d.getCompanyCd()).deptId(d.getDeptId())
                    .userNm("User " + i).email(uid.toLowerCase() + "@hd.local").status("A")
                    .build());
        }
    }

    private Map<String, List<Menu>> seedMenusAndApis() {
        Map<String, List<Menu>> leavesBySystem = new HashMap<>();
        Random rnd = new Random(7);
        int totalApiTarget = apiCount;
        int remaining = totalApiTarget;
        int sysIdx = 0;

        for (String system : SYSTEMS) {
            String[] domains = switch (system) {
                case "ERP" -> DOMAINS_ERP;
                case "MES" -> DOMAINS_MES;
                default -> DOMAINS_PORTAL;
            };

            int sysApiQuota = (sysIdx == SYSTEMS.length - 1) ? remaining : (totalApiTarget / SYSTEMS.length);
            remaining -= sysApiQuota;
            sysIdx++;

            List<Menu> leaves = new ArrayList<>();

            // Build folder per domain, with 4–6 leaf menus each
            for (String domain : domains) {
                Menu folder = Menu.builder()
                        .systemCd(system).menuType("F")
                        .menuCd(domain.toUpperCase())
                        .menuNm(displayName(domain) + "관리")
                        .icon("folder").sortOrder(0).isVisible("Y").status("A")
                        .createdBy("seed").updatedBy("seed").build();
                folder = menuRepo.save(folder);

                int leafN = 4 + rnd.nextInt(3);
                for (int k = 0; k < leafN; k++) {
                    String[] subs = {"list","detail","create","approval","report","summary"};
                    String sub = subs[k % subs.length];
                    Menu leaf = Menu.builder()
                            .systemCd(system).parentMenuId(folder.getMenuId()).menuType("M")
                            .menuCd(folder.getMenuCd() + "_" + sub.toUpperCase())
                            .menuNm(displayName(domain) + " " + displayName(sub))
                            .icon("file").sortOrder(k).isVisible("Y").status("A")
                            .createdBy("seed").updatedBy("seed").build();
                    leaf = menuRepo.save(leaf);
                    leaves.add(leaf);

                    MenuImpl impl = MenuImpl.builder()
                            .menuId(leaf.getMenuId())
                            .routePath("/" + domain + "/" + sub)
                            .routeName(folder.getMenuCd() + "_" + sub.toUpperCase())
                            .componentName(displayName(domain) + displayName(sub))
                            .componentPath("@/views/" + domain + "/" + sub + ".vue")
                            .routeMeta(Map.of("requiresAuth", true, "title", leaf.getMenuNm()))
                            .hasLayout("Y").isFullScreen("N").isModal("N")
                            .mobileSupported("Y")
                            .build();
                    menuImplRepo.save(impl);

                    // bind actions for this leaf
                    String[] menuActions = {"R","C","U","D"};
                    for (String a : menuActions) {
                        menuActionRepo.save(MenuAction.builder()
                                .menuId(leaf.getMenuId()).actionCd(a).build());
                    }
                }
            }
            leavesBySystem.put(system, leaves);

            // ===== APIs =====
            int perDomain = Math.max(1, sysApiQuota / domains.length);
            int apisCreated = 0;
            for (String domain : domains) {
                int domainApis = (domain.equals(domains[domains.length - 1]))
                        ? sysApiQuota - apisCreated
                        : perDomain;
                for (int i = 0; i < domainApis; i++) {
                    String method = METHODS[i % METHODS.length];
                    int variantDepth = 2 + (i % 4);   // 2..5
                    String[] resources = {"items","docs","forms","sheets","tasks","tickets","reports","drafts"};
                    String res = resources[i % resources.length];
                    StringBuilder url = new StringBuilder("/api/").append(domain).append('/').append(res);
                    for (int d = 2; d < variantDepth; d++) {
                        url.append("/g").append(i / 10).append("s").append(d);
                    }
                    url.append("/n").append(i);
                    if (i % 3 == 0) url.append("/{id}");
                    int depth = UrlUtils.depth(url.toString());

                    ApiEntity api = ApiEntity.builder()
                            .systemCd(system).httpMethod(method).urlPattern(url.toString())
                            .urlDepth(depth).shardSeg(extractDomainSeg(url.toString()))
                            .serviceNm(domain + "-svc")
                            .description("Sample API #" + i + " for " + domain)
                            .status("A").build();
                    api = apiRepo.save(api);
                    apisCreated++;

                    // map this api to a random leaf in the same domain w/ method-derived action
                    String act = methodToAction(method);
                    Menu target = leaves.stream()
                            .filter(l -> l.getMenuCd().startsWith(domain.toUpperCase()))
                            .findAny().orElse(null);
                    if (target != null) {
                        menuActionApiRepo.save(MenuActionApi.builder()
                                .menuId(target.getMenuId()).actionCd(act).apiId(api.getApiId())
                                .build());
                    }
                }
            }
            log.info("System {} → {} menus / {} APIs", system, leaves.size(), apisCreated);
        }
        return leavesBySystem;
    }

    private void seedPermissions(Map<String, List<Menu>> leavesBySystem) {
        Random rnd = new Random(99);
        List<Company> companies = companyRepo.findAll();
        List<Dept> depts = deptRepo.findAll();
        List<UserEntity> users = userRepo.findAll();
        Set<String> dedupe = new HashSet<>();

        int permCount = 0;
        for (String system : SYSTEMS) {
            List<Menu> leaves = leavesBySystem.get(system);
            permCount += seedPermBlock(dedupe, 30,
                    () -> companies.get(rnd.nextInt(companies.size())).getCompanyCd(),
                    cc -> cc, "C", system, leaves, rnd);
            permCount += seedPermBlock(dedupe, 60,
                    () -> depts.get(rnd.nextInt(depts.size())).getDeptId(),
                    did -> depts.stream().filter(d -> d.getDeptId().equals(did)).findFirst().get().getCompanyCd(),
                    "D", system, leaves, rnd);
            permCount += seedPermBlock(dedupe, 200,
                    () -> users.get(rnd.nextInt(users.size())).getUserId(),
                    uid -> users.stream().filter(u -> u.getUserId().equals(uid)).findFirst().get().getCompanyCd(),
                    "U", system, leaves, rnd);
        }
        log.info("Seeded {} permissions", permCount);
    }

    private int seedPermBlock(Set<String> dedupe, int count,
                              java.util.function.Supplier<String> subjectIdSupplier,
                              java.util.function.Function<String, String> companyResolver,
                              String subjectType, String system, List<Menu> leaves, Random rnd) {
        int saved = 0, attempts = 0;
        while (saved < count && attempts < count * 5) {
            attempts++;
            String subjectId = subjectIdSupplier.get();
            String companyCd = companyResolver.apply(subjectId);
            Menu m = leaves.get(rnd.nextInt(leaves.size()));
            String act = ACTIONS[rnd.nextInt(4)];
            String key = system + "|" + companyCd + "|" + subjectType + "|" + subjectId + "|M|" + m.getMenuId() + "|" + act;
            if (!dedupe.add(key)) continue;
            permissionRepo.save(Permission.builder()
                    .systemCd(system).companyCd(companyCd)
                    .subjectType(subjectType).subjectId(subjectId)
                    .targetType("M").targetId(m.getMenuId())
                    .actionCd(act)
                    .createdBy("seed").updatedBy("seed").build());
            saved++;
        }
        return saved;
    }

    private static String extractDomainSeg(String urlPattern) {
        List<String> segs = UrlUtils.segments(urlPattern);
        return segs.size() > 1 ? segs.get(1) : "_root";
    }

    private static String methodToAction(String method) {
        return switch (method) {
            case "POST" -> "C";
            case "PUT", "PATCH" -> "U";
            case "DELETE" -> "D";
            default -> "R";
        };
    }

    private static String displayName(String s) {
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
