package com.teammind.performance;

import com.teammind.entity.PerformanceRecord;
import com.teammind.repository.PerformanceRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DriftDetectorTest {

    private PerformanceRecordRepository recordRepo;
    private DriftDetector detector;

    @BeforeEach
    void setUp() {
        recordRepo = mock(PerformanceRecordRepository.class);
        detector = new DriftDetector(recordRepo);
    }

    @Test
    @DisplayName("空数据返回空告警")
    void emptyDataReturnsNoAlerts() {
        when(recordRepo.findByProjectId("p-1")).thenReturn(List.of());
        assertTrue(detector.detect("p-1").isEmpty());
    }

    @Test
    @DisplayName("样本不足时不产生告警")
    void insufficientSamplesNoAlert() {
        // 只有 2 条记录，少于 MIN_SAMPLES_SHORT=5
        PerformanceRecord r = buildRecord("p-1", "codex", "LEAD", 0.9, 2);
        when(recordRepo.findByProjectId("p-1")).thenReturn(List.of(r));

        var alerts = detector.detect("p-1");
        assertTrue(alerts.isEmpty());
    }

    @Test
    @DisplayName("漂移超过阈值产生告警")
    void driftAboveThresholdCreatesAlert() {
        LocalDateTime now = LocalDateTime.now();
        // 近期：3 条高成功率记录（>= MIN_SAMPLES_SHORT=3）
        PerformanceRecord r1 = buildRecordWithDate("p-1", "codex", "LEAD", 0.95, 8, now.minusDays(2));
        PerformanceRecord r2 = buildRecordWithDate("p-1", "codex", "LEAD", 0.90, 7, now.minusDays(4));
        PerformanceRecord r3 = buildRecordWithDate("p-1", "codex", "LEAD", 0.92, 9, now.minusDays(6));
        // 基线：3 条低成功率记录（>= MIN_SAMPLES_LONG=3）
        PerformanceRecord o1 = buildRecordWithDate("p-1", "codex", "LEAD", 0.50, 5, now.minusDays(60));
        PerformanceRecord o2 = buildRecordWithDate("p-1", "codex", "LEAD", 0.48, 6, now.minusDays(65));
        PerformanceRecord o3 = buildRecordWithDate("p-1", "codex", "LEAD", 0.52, 4, now.minusDays(70));

        when(recordRepo.findByProjectId("p-1")).thenReturn(List.of(r1, r2, r3, o1, o2, o3));

        var alerts = detector.detect("p-1");
        assertFalse(alerts.isEmpty());
        assertEquals("IMPROVING", alerts.get(0).trend());
        assertEquals("codex", alerts.get(0).pluginId());
        assertEquals("LEAD", alerts.get(0).role());
    }

    @Test
    @DisplayName("下降趋势正确标记为 DECLINING")
    void decliningTrendMarkedCorrectly() {
        LocalDateTime now = LocalDateTime.now();
        PerformanceRecord r1 = buildRecordWithDate("p-1", "claude-code", "REVIEWER", 0.40, 8, now.minusDays(2));
        PerformanceRecord r2 = buildRecordWithDate("p-1", "claude-code", "REVIEWER", 0.38, 7, now.minusDays(4));
        PerformanceRecord r3 = buildRecordWithDate("p-1", "claude-code", "REVIEWER", 0.42, 9, now.minusDays(6));
        PerformanceRecord o1 = buildRecordWithDate("p-1", "claude-code", "REVIEWER", 0.80, 15, now.minusDays(60));
        PerformanceRecord o2 = buildRecordWithDate("p-1", "claude-code", "REVIEWER", 0.78, 12, now.minusDays(65));
        PerformanceRecord o3 = buildRecordWithDate("p-1", "claude-code", "REVIEWER", 0.82, 14, now.minusDays(70));

        when(recordRepo.findByProjectId("p-1")).thenReturn(List.of(r1, r2, r3, o1, o2, o3));

        var alerts = detector.detect("p-1");
        assertFalse(alerts.isEmpty());
        assertEquals("DECLINING", alerts.get(0).trend());
    }

    @Test
    @DisplayName("getTrendSummary 返回有效摘要")
    void trendSummaryValid() {
        when(recordRepo.findByProjectId("p-1")).thenReturn(List.of());
        var summary = detector.getTrendSummary("p-1");
        assertEquals(0, summary.improving());
        assertEquals(0, summary.declining());
        assertEquals(0, summary.stable());
        assertEquals("STABLE", summary.overallStatus());
    }

    private PerformanceRecord buildRecord(String projectId, String pluginId, String role,
                                           double rate, int samples) {
        return buildRecordWithDate(projectId, pluginId, role, rate, samples, LocalDateTime.now());
    }

    private PerformanceRecord buildRecordWithDate(String projectId, String pluginId, String role,
                                                   double rate, int samples, LocalDateTime date) {
        PerformanceRecord r = new PerformanceRecord();
        r.setProjectId(projectId);
        r.setPluginId(pluginId);
        r.setRole(role);
        r.setSuccessRate(rate);
        r.setSampleSize(samples);
        r.setLastUpdated(date);
        r.setCreatedAt(date);
        return r;
    }
}
