package com.teammind.performance;

import com.teammind.entity.PerformanceRecord;
import com.teammind.repository.PerformanceRecordRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * TeamRecommender — 基于历史数据推荐最优团队配置
 */
@Slf4j
@Component
public class TeamRecommender {

    private static final int MIN_TASKS = 30;
    private static final int MIN_SAMPLES_PER_ROLE = 5;
    private static final double LOW_PERFORMANCE_THRESHOLD = 0.70;

    private final PerformanceRecordRepository recordRepo;

    public TeamRecommender(PerformanceRecordRepository recordRepo) {
        this.recordRepo = recordRepo;
    }

    public Optional<TeamRecommendation> recommend(String projectId) {
        List<PerformanceRecord> records = recordRepo.findByProjectId(projectId);
        if (records.isEmpty()) return Optional.empty();

        int totalTasks = records.stream()
                .mapToInt(r -> r.getSampleSize() != null ? r.getSampleSize() : 0)
                .sum();
        if (totalTasks < MIN_TASKS) {
            log.debug("[TeamRecommender] Insufficient data for {}: {} tasks", projectId, totalTasks);
            return Optional.empty();
        }

        Map<String, Map<String, PerformanceRecord>> byRolePlugin = records.stream()
                .filter(r -> r.getRole() != null && r.getPluginId() != null
                        && r.getSampleSize() != null && r.getSampleSize() >= MIN_SAMPLES_PER_ROLE)
                .collect(Collectors.groupingBy(
                        PerformanceRecord::getRole,
                        Collectors.toMap(
                                PerformanceRecord::getPluginId,
                                r -> r,
                                (a, b) -> a.getSuccessRate() >= b.getSuccessRate() ? a : b
                        )
                ));

        List<Issue> issues = new ArrayList<>();
        Map<String, String> recommendedTeam = new LinkedHashMap<>();

        for (var roleEntry : byRolePlugin.entrySet()) {
            String role = roleEntry.getKey();
            var bestEntry = roleEntry.getValue().entrySet().stream()
                    .max(Map.Entry.comparingByValue(Comparator.comparingDouble(PerformanceRecord::getSuccessRate)));

            if (bestEntry.isEmpty()) continue;
            PerformanceRecord best = bestEntry.get().getValue();
            recommendedTeam.put(role, best.getPluginId());

            if (best.getSuccessRate() < LOW_PERFORMANCE_THRESHOLD) {
                issues.add(new Issue(role, best.getPluginId(),
                        Math.round(best.getSuccessRate() * 10000.0) / 100.0,
                        best.getSampleSize(),
                        String.format("%s 在 %s 角色上成功率仅 %.0f%%（%d 次）",
                                best.getPluginId(), role,
                                best.getSuccessRate() * 100, best.getSampleSize())));
            }
        }

        if (issues.isEmpty()) return Optional.empty();

        log.info("[TeamRecommender] Generated recommendation for {}: {} issues", projectId, issues.size());
        return Optional.of(new TeamRecommendation(projectId, totalTasks, recommendedTeam, issues));
    }

    /**
     * 团队配置推荐
     */
    public record TeamRecommendation(
            String projectId,
            int totalTasks,
            Map<String, String> recommendedTeam,
            List<Issue> issues
    ) {}

    /**
     * 配置不匹配问题
     */
    public record Issue(
            String role,
            String currentPlugin,
            double currentScore,
            int sampleSize,
            String reason
    ) {}
}
