package com.teammind.repository;

import com.teammind.entity.Mission;
import com.teammind.entity.Mission.MissionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Mission Repository
 */
@Repository
public interface MissionRepository extends JpaRepository<Mission, String> {

    List<Mission> findByStatus(MissionStatus status);

    Page<Mission> findByStatusIn(List<MissionStatus> statuses, Pageable pageable);

    List<Mission> findByOrderByCreatedAtDesc();

    Page<Mission> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<Mission> findByCompletedAtBetween(LocalDateTime start, LocalDateTime end);

    long countByStatus(MissionStatus status);

    long countByStatusIn(List<MissionStatus> statuses);
}
