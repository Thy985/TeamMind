package com.teammind.repository;

import com.teammind.entity.Artifact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ArtifactRepository extends JpaRepository<Artifact, String> {
    List<Artifact> findByInvocationId(String invocationId);
}
