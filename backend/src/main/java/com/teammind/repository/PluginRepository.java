package com.teammind.repository;

import com.teammind.entity.Plugin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PluginRepository extends JpaRepository<Plugin, String> {
    List<Plugin> findByEnabledTrue();
    List<Plugin> findByPluginType(Plugin.PluginType type);
    List<Plugin> findByIdIn(List<String> ids);
}
