package com.teammind.repository;

import com.teammind.entity.RoutingLesson;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoutingLessonRepository extends JpaRepository<RoutingLesson, String> {
    List<RoutingLesson> findByProjectId(String projectId);
    List<RoutingLesson> findByProjectIdAndPluginId(String projectId, String pluginId);
    Optional<RoutingLesson> findByKey(String key);
}
