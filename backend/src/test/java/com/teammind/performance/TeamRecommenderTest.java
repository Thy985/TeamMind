package com.teammind.performance;

import com.teammind.entity.PerformanceRecord;
import com.teammind.repository.PerformanceRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TeamRecommenderTest {

    private PerformanceRecordRepository recordRepo;
    private TeamRecommender recommender;

    @BeforeEach
    void setUp() {
        recordRepo = mock(PerformanceRecordRepository.class);
        recommender = new TeamRecommender(recordRepo);
    }

    @Test
    @DisplayName("无数据时返回 empty")
    void noDataReturnsEmpty() {
        when(recordRepo.findByProjectId("p-1")).thenReturn(List.of());
        assertTrue(recommender.recommend("p-1").isEmpty());
    }

    @Test
    @DisplayName("样本不足时返回 empty")
    void insufficientSamplesReturnsEmpty() {
        // 只有 2 条记录，totalTasks < 30
        PerformanceRecord r = buildRecord("p-1", "codex", "LEAD", 0.9, 2);
        when(recordRepo.findByProjectId("p-1")).thenReturn(List.of(r));
        assertTrue(recommender.recommend("p-1").isEmpty());
    }

    @Test
    @DisplayName("表现良好的配置不产生推荐")
    void goodPerformanceNoRecommendation() {
        // 35 次任务，成功率 > 70%
        PerformanceRecord r = buildRecord("p-1", "codex", "LEAD", 0.92, 20);
        PerformanceRecord r2 = buildRecord("p-1", "codex", "TESTER", 0.88, 15);
        when(recordRepo.findByProjectId("p-1")).thenReturn(List.of(r, r2));

        assertTrue(recommender.recommend("p-1").isEmpty());
    }

    @Test
    @DisplayName("低成功率角色产生推荐和 issue")
    void lowPerformanceGeneratesIssue() {
        PerformanceRecord bad = buildRecord("p-1", "claude-code", "LEAD", 0.55, 15);
        PerformanceRecord ok = buildRecord("p-1", "codex", "TESTER", 0.85, 18);
        when(recordRepo.findByProjectId("p-1")).thenReturn(List.of(bad, ok));

        var rec = recommender.recommend("p-1");
        assertTrue(rec.isPresent());
        assertEquals("p-1", rec.get().projectId());
        assertEquals(33, rec.get().totalTasks());
        assertTrue(rec.get().issues().stream()
                .anyMatch(i -> i.role().equals("LEAD") && i.currentPlugin().equals("claude-code")));
    }

    private PerformanceRecord buildRecord(String projectId, String pluginId, String role,
                                           double rate, int samples) {
        PerformanceRecord r = new PerformanceRecord();
        r.setProjectId(projectId);
        r.setPluginId(pluginId);
        r.setRole(role);
        r.setSuccessRate(rate);
        r.setSampleSize(samples);
        return r;
    }
}
