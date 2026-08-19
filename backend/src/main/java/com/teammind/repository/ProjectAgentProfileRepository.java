package com.teammind.repository;

import com.teammind.entity.ProjectAgentProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProjectAgentProfileRepository extends JpaRepository<ProjectAgentProfile, String> {
}
