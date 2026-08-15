package com.teammind.performance;

import com.teammind.TeamMindApplication;
import com.teammind.auth.JwtUtil;
import com.teammind.entity.PerformanceRecord;
import com.teammind.entity.User;
import com.teammind.repository.PerformanceRecordRepository;
import com.teammind.repository.TaskExecutionRepository;
import com.teammind.repository.UserRepository;
import com.teammind.common.TaskState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * 端到端集成测试：PerformanceTracker + DriftDetector + TeamRecommender + MissionControlController
 */
@SpringBootTest(classes = TeamMindApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(locations = "classpath:application-test.yml")
class PerformanceE2EIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private PerformanceRecordRepository recordRepo;

    @Autowired
    private TaskExecutionRepository taskRepo;

    @Autowired
    private PerformanceTracker tracker;

    @Autowired
    private DriftDetector driftDetector;

    @Autowired
    private TeamRecommender recommender;

    @Autowired
    private JwtUtil jwtUtil;

    @MockBean
    private UserRepository userRepository;

    private final TestRestTemplate restTemplate = new TestRestTemplate();
    private String baseUrl;
    private static final String PROJECT_ID = "e2e-test-project";
    private String authToken;

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port + "/api/mission-control";
        // 生成一个有效的 JWT token
        authToken = jwtUtil.generateToken("test-user", "test-user", List.of("admin"), List.of());
        User testUser = new User();
        testUser.setId("test-user");
        testUser.setUsername("test-user");
        testUser.setEnabled(true);
        when(userRepository.findById("test-user")).thenReturn(Optional.of(testUser));
        recordRepo.deleteAll();
        taskRepo.deleteAll();
    }

    private HttpHeaders headers() {
        HttpHeaders h = new HttpHeaders();
        h.set("Authorization", "Bearer " + authToken);
        return h;
    }

    private <T> ResponseEntity<T> get(String url, Class<T> cls) {
        return restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers()), cls);
    }

    private <T> ResponseEntity<T> post(String url, Object body, Class<T> cls) {
        return restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(body, headers()), cls);
    }

    private <T> ResponseEntity<T> put(String url, Object body, Class<T> cls) {
        return restTemplate.exchange(url, HttpMethod.PUT, new HttpEntity<>(body, headers()), cls);
    }

    // ═══════════════════════════════════════════════════════════
    // 场景 1：空项目 → 所有接口返回空/零值结构
    // ═══════════════════════════════════════════════════════════

    @Test
    @DisplayName("空项目的 overview 返回零值")
    void emptyProjectOverviewReturnsZeros() {
        ResponseEntity<Map> resp = get(baseUrl + "/project/" + PROJECT_ID + "/overview", Map.class);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        Map<String, Object> body = resp.getBody();
        assertNotNull(body);
        assertEquals(PROJECT_ID, body.get("projectId"));
        assertEquals(0, body.get("totalTasks"));
        assertEquals(0, body.get("completed"));
        assertEquals(0, body.get("failed"));
        assertEquals(0, body.get("pending"));
        assertTrue(body.containsKey("controlMode"));
    }

    @Test
    @DisplayName("空项目的 drift 返回空列表")
    void emptyProjectDriftReturnsEmpty() {
        ResponseEntity<List> resp = get(baseUrl + "/project/" + PROJECT_ID + "/drift", List.class);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertNotNull(resp.getBody());
    }

    // ═══════════════════════════════════════════════════════════
    // 场景 2：注入历史数据 → 推荐和漂移检测能工作
    // ═══════════════════════════════════════════════════════════

    @Test
    @DisplayName("注入 35+ 条记录后 recommendation 端点返回数据")
    void recommendationReturnsWhenEnoughData() {
        seedSufficientData();
        ResponseEntity<Map> resp = get(baseUrl + "/project/" + PROJECT_ID + "/recommendation", Map.class);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertNotNull(resp.getBody());
    }

    @Test
    @DisplayName("注入漂移数据后 drift 端点返回告警")
    void driftAlertsDetected() {
        seedDriftData();
        ResponseEntity<List> resp = get(baseUrl + "/project/" + PROJECT_ID + "/drift", List.class);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        List<?> alerts = resp.getBody();
        assertNotNull(alerts);
        assertFalse(alerts.isEmpty(), "应检测到至少一条漂移告警");
    }

    @Test
    @DisplayName("recalculate 端点不抛异常并返回正确结构")
    void recalculateEndpointWorks() {
        seedSufficientData();
        ResponseEntity<Map> resp = post(baseUrl + "/project/" + PROJECT_ID + "/recalculate", null, Map.class);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        Map<String, Object> body = resp.getBody();
        assertNotNull(body);
        assertTrue(body.containsKey("driftAlerts"));
        assertTrue(body.containsKey("recommendation"));
        assertEquals("Recalculation complete", body.get("message"));
    }

    @Test
    @DisplayName("history 端点返回排序后的任务列表")
    void historyEndpointReturnsList() {
        for (int i = 0; i < 3; i++) {
            var exec = new com.teammind.entity.TaskExecution();
            exec.setId("task-" + i);
            exec.setProjectId(PROJECT_ID);
            exec.setObjective("Test objective " + i);
            exec.setState(i < 2 ? TaskState.DONE : TaskState.FAILED);
            exec.setCurrentAgentId("codex");
            exec.setCurrentRole("LEAD");
            exec.setTaskTypeId("bug_fix");
            exec.setDurationMs(5000L);
            exec.setRetryCount(0);
            exec.setCreatedAt(java.time.LocalDateTime.now().minusHours(3 - i));
            taskRepo.save(exec);
        }
        ResponseEntity<List> resp = get(baseUrl + "/project/" + PROJECT_ID + "/history?limit=10", List.class);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        List<?> history = resp.getBody();
        assertNotNull(history);
        assertEquals(3, history.size());
    }

    @Test
    @DisplayName("profile 端点返回有效的趋势摘要")
    void profileEndpointReturnsTrendSummary() {
        seedSufficientData();
        ResponseEntity<Map> resp = get(baseUrl + "/project/" + PROJECT_ID + "/profile", Map.class);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        Map<String, Object> body = resp.getBody();
        assertNotNull(body);
        assertTrue(body.containsKey("trend"));
        assertTrue(body.containsKey("byRole"));
        assertTrue(body.containsKey("totalRecords"));
    }

    @Test
    @DisplayName("control-mode GET/PUT 正常工作")
    void controlModeEndpointsWork() {
        ResponseEntity<Map> getResp = get(baseUrl + "/project/" + PROJECT_ID + "/control-mode", Map.class);
        assertEquals(HttpStatus.OK, getResp.getStatusCode());
        assertEquals(PROJECT_ID, getResp.getBody().get("projectId"));
        assertEquals("SUPERVISED", getResp.getBody().get("controlMode"));

        ResponseEntity<Map> putResp = put(
                baseUrl + "/project/" + PROJECT_ID + "/control-mode",
                Map.of("controlMode", "AUTOMATED"),
                Map.class);
        assertEquals(HttpStatus.OK, putResp.getStatusCode());
        assertEquals("AUTOMATED", putResp.getBody().get("controlMode"));
        assertTrue((Boolean) putResp.getBody().get("success"));
    }

    // ═══════════════════════════════════════════════════════════
    // 场景 3：PerformanceTracker 更新后影响推荐结果
    // ═══════════════════════════════════════════════════════════

    @Test
    @DisplayName("tracker 更新后 recommendation 反映新数据")
    void trackerUpdatesAffectRecommendations() {
        seedSufficientData();
        var exec = new com.teammind.entity.TaskExecution();
        exec.setId("task-new");
        exec.setProjectId(PROJECT_ID);
        exec.setObjective("New task");
        exec.setState(TaskState.DONE);
        exec.setCurrentAgentId("codex");
        exec.setCurrentRole("LEAD");
        exec.setTaskTypeId("implementation");
        exec.setDurationMs(3000L);
        exec.setRetryCount(0);
        exec.setCreatedAt(java.time.LocalDateTime.now());
        taskRepo.save(exec);
        tracker.onTaskCompleted("task-new", true);

        ResponseEntity<Map> resp = get(baseUrl + "/project/" + PROJECT_ID + "/recommendation", Map.class);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertNotNull(resp.getBody());
    }

    // ═══════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════

    private void seedSufficientData() {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        // 创建 4 条高成功率记录（每条 sampleSize >= 10，总共 > 30 任务）
        saveRecord(PROJECT_ID, "codex", "LEAD", "implementation", 0.92, 12, now.minusDays(1));
        saveRecord(PROJECT_ID, "codex", "TESTER", "test_generation", 0.88, 10, now.minusDays(2));
        saveRecord(PROJECT_ID, "claude-code", "REVIEWER", "code_review", 0.85, 8, now.minusDays(3));
        // 低成功率记录（触发 issue）
        saveRecord(PROJECT_ID, "claude-code", "SECURITY", "security_review", 0.55, 6, now.minusDays(5));
    }

    private void seedDriftData() {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        // 近期高成功率（近 7 天，>= MIN_SAMPLES_SHORT=3）
        saveRecord(PROJECT_ID, "codex", "LEAD", "implementation", 0.95, 8, now.minusDays(2));
        saveRecord(PROJECT_ID, "codex", "LEAD", "implementation", 0.92, 7, now.minusDays(4));
        saveRecord(PROJECT_ID, "codex", "LEAD", "implementation", 0.90, 9, now.minusDays(6));
        // 基线低成功率（60 天前，>= MIN_SAMPLES_LONG=3，但在 long window 内，不在 short window）
        saveRecord(PROJECT_ID, "codex", "LEAD", "implementation", 0.50, 5, now.minusDays(60));
        saveRecord(PROJECT_ID, "codex", "LEAD", "implementation", 0.48, 6, now.minusDays(65));
        saveRecord(PROJECT_ID, "codex", "LEAD", "implementation", 0.52, 4, now.minusDays(70));
    }

    private void saveRecord(String projectId, String pluginId, String role,
                            String taskType, double rate, int samples, java.time.LocalDateTime date) {
        PerformanceRecord r = new PerformanceRecord();
        r.setProjectId(projectId);
        r.setPluginId(pluginId);
        r.setRole(role);
        r.setTaskTypeId(taskType);
        r.setSuccessRate(rate);
        r.setSampleSize(samples);
        r.setAvgIterations(1.5);
        r.setAvgDurationMs(5000L);
        r.setLastUpdated(date);
        r.setCreatedAt(date);
        recordRepo.save(r);
    }
}
