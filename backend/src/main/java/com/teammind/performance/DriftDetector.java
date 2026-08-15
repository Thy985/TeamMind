package com.teammind.performance;

import com.teammind.entity.PerformanceRecord;
import com.teammind.repository.PerformanceRecordRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * DriftDetector — 检测角色表现漂移
 *
 * 算法：
 *   1. 对每个 (project, plugin, role) 组合，获取所有历史记录
 *   2. 按时间切分为"近期"（30天）和"基线"（90天）
 *   3. 如果近期成功率与基线差值 > 10% 且样本足够，产生 ALERT
 *   4. 趋势方向：DECLINING / IMPROVING
 */
@Slf4j
@Component
public class DriftDetector {

    private static final double DRIFT_THRESHOLD = 0.10;   // 成功率变化超过 10%
    private static final int MIN_SAMPLES_SHORT = 3;
    private static final int MIN_SAMPLES_LONG = 3;
    private static final int WINDOW_DAYS_SHORT = 30;
    private static final int WINDOW_DAYS_LONG = 90;

    private final PerformanceRecordRepository recordRepo;

    public DriftDetector(PerformanceRecordRepository recordRepo) {
        this.recordRepo = recordRepo;
    }

    /**
     * 检测指定项目的所有角色漂移
     */
    public List<DriftAlert> detect(String projectId) {
        List<PerformanceRecord> records = new ArrayList<>(recordRepo.findByProjectId(projectId));
        List<DriftAlert> alerts = new ArrayList<>();

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime shortStart = now.minusDays(WINDOW_DAYS_SHORT);
        LocalDateTime longStart = now.minusDays(WINDOW_DAYS_LONG);

        // 按 (pluginId, role) 分组
        records.sort(Comparator.comparing(PerformanceRecord::getLastUpdated,
                Comparator.nullsLast(Comparator.reverseOrder())));

        for (int i = 0; i < records.size(); ) {
            PerformanceRecord current = records.get(i);
            List<PerformanceRecord> sameGroup = new ArrayList<>();
            while (i < records.size()
                    && eq(current.getPluginId(), records.get(i).getPluginId())
                    && eq(current.getRole(), records.get(i).getRole())) {
                sameGroup.add(records.get(i++));
            }

            double shortRate = avgRate(sameGroup, shortStart);
            int shortCount = countAbove(sameGroup, shortStart);
            double longRate = avgRate(sameGroup, longStart);
            int longCount = countAbove(sameGroup, longStart);

            if (shortCount < MIN_SAMPLES_SHORT || longCount < MIN_SAMPLES_LONG) continue;

            double drift = shortRate - longRate;
            if (Math.abs(drift) < DRIFT_THRESHOLD) continue;

            alerts.add(new DriftAlert(
                    projectId,
                    current.getPluginId(),
                    current.getRole(),
                    "success_rate",
                    drift > 0 ? "IMPROVING" : "DECLINING",
                    Math.round(drift * 10000.0) / 100.0,
                    WINDOW_DAYS_SHORT,
                    shortStart.toString(),
                    buildRecommendation(current.getRole(), current.getPluginId(), drift)
            ));
        }

        if (!alerts.isEmpty()) {
            log.warn("[DriftDetector] {} drift alerts for project {}", alerts.size(), projectId);
        }
        return alerts;
    }

    /**
     * 趋势摘要（供 Mission Control 展示）
     */
    public TrendSummary getTrendSummary(String projectId) {
        List<PerformanceRecord> records = recordRepo.findByProjectId(projectId);
        LocalDateTime cutoff = LocalDateTime.now().minusDays(WINDOW_DAYS_SHORT);

        int improving = 0, declining = 0, stable = 0;
        for (PerformanceRecord r : records) {
            if (r.getLastUpdated() == null || r.getLastUpdated().isBefore(cutoff)) continue;
            if (r.getSampleSize() < MIN_SAMPLES_LONG) continue;
            if (r.getSuccessRate() > 0.60) improving++;
            else if (r.getSuccessRate() < 0.40) declining++;
            else stable++;
        }
        return new TrendSummary(improving, declining, stable, records.size());
    }

    // ─── Internal helpers ──────────────────────────────────────

    private double avgRate(List<PerformanceRecord> records, LocalDateTime cutoff) {
        return records.stream()
                .filter(r -> r.getLastUpdated() != null && !r.getLastUpdated().isBefore(cutoff))
                .mapToDouble(r -> r.getSuccessRate() != null ? r.getSuccessRate() : 0.5)
                .average().orElse(0.5);
    }

    private int countAbove(List<PerformanceRecord> records, LocalDateTime cutoff) {
        return (int) records.stream()
                .filter(r -> r.getLastUpdated() != null && !r.getLastUpdated().isBefore(cutoff))
                .count();
    }

    private boolean eq(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    private String buildRecommendation(String role, String pluginId, double drift) {
        if (drift < 0) {
            return String.format("%s 在 %s 角色上成功率下降 %.0f%%，建议评估是否更换 Agent",
                    pluginId, role, Math.abs(drift) * 100);
        }
        return String.format("%s 在 %s 角色上成功率提升 %.0f%%，建议增加该角色负载",
                pluginId, role, drift * 100);
    }

    /**
     * 漂移告警
     */
    public record DriftAlert(
            String projectId,
            String pluginId,
            String role,
            String metric,
            String trend,
            double change,
            int windowDays,
            String sinceDate,
            String recommendation
    ) {}

    /**
     * 趋势摘要
     */
    public record TrendSummary(int improving, int declining, int stable, int totalRecords) {
        public String overallStatus() {
            if (declining > improving) return "DECLINING";
            if (improving > declining) return "IMPROVING";
            return "STABLE";
        }
    }
}
