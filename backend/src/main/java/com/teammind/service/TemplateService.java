package com.teammind.service;

import com.teammind.dto.*;
import com.teammind.entity.TeamTemplate;
import com.teammind.repository.TeamTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Template Service
 * 
 * 处理团队模板相关的业务逻辑
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TemplateService {

    private final TeamTemplateRepository templateRepository;

    @Value("${teammind.templates-path:~/.teammind/templates}")
    private String templatesPath;

    /**
     * 获取所有模板
     */
    public List<TemplateDTO> listTemplates() {
        List<TeamTemplate> templates = templateRepository.findAll();
        return templates.stream().map(this::toDTO).toList();
    }

    /**
     * 获取公开模板
     */
    public List<TemplateDTO> listPublicTemplates() {
        List<TeamTemplate> templates = templateRepository.findByIsPublicTrue();
        return templates.stream().map(this::toDTO).toList();
    }

    /**
     * 获取我的模板
     */
    public List<TemplateDTO> listMyTemplates() {
        List<TeamTemplate> templates = templateRepository.findByIsPublicFalse();
        return templates.stream().map(this::toDTO).toList();
    }

    /**
     * 按分类获取模板
     */
    public Map<String, List<TemplateDTO>> listTemplatesByCategory() {
        List<TeamTemplate> templates = templateRepository.findAll();
        Map<String, List<TemplateDTO>> result = new HashMap<>();

        for (TeamTemplate template : templates) {
            String category = template.getCategory() != null ? template.getCategory() : "Other";
            result.computeIfAbsent(category, k -> new ArrayList<>()).add(toDTO(template));
        }

        return result;
    }

    /**
     * 获取模板详情
     */
    public TemplateDTO getTemplate(String id) {
        TeamTemplate template = templateRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Template not found: " + id));
        return toDTO(template);
    }

    /**
     * 创建模板
     */
    @Transactional
    public TemplateDTO createTemplate(CreateTemplateRequest request) {
        TeamTemplate template = TeamTemplate.builder()
                .id(UUID.randomUUID().toString())
                .name(request.getName())
                .description(request.getDescription())
                .icon(request.getIcon() != null ? request.getIcon() : "📄")
                .category(request.getCategory())
                .agents(request.getAgents())
                .isPublic(request.getIsPublic() != null ? request.getIsPublic() : false)
                .usageCount(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        template = templateRepository.save(template);

        // 保存到 Markdown 文件
        saveTemplateConfig(template);

        return toDTO(template);
    }

    /**
     * 更新模板
     */
    @Transactional
    public TemplateDTO updateTemplate(String id, UpdateTemplateRequest request) {
        TeamTemplate template = templateRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Template not found: " + id));

        if (request.getName() != null) {
            template.setName(request.getName());
        }
        if (request.getDescription() != null) {
            template.setDescription(request.getDescription());
        }
        if (request.getIcon() != null) {
            template.setIcon(request.getIcon());
        }
        if (request.getCategory() != null) {
            template.setCategory(request.getCategory());
        }
        if (request.getAgents() != null) {
            template.setAgents(request.getAgents());
        }
        if (request.getIsPublic() != null) {
            template.setIsPublic(request.getIsPublic());
        }
        template.setUpdatedAt(LocalDateTime.now());

        template = templateRepository.save(template);

        // 更新 Markdown 文件
        saveTemplateConfig(template);

        return toDTO(template);
    }

    /**
     * 删除模板
     */
    @Transactional
    public void deleteTemplate(String id) {
        TeamTemplate template = templateRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Template not found: " + id));

        // 删除配置文件
        if (template.getConfigPath() != null) {
            try {
                Path configPath = Paths.get(templatesPath, template.getConfigPath());
                Files.deleteIfExists(configPath);
            } catch (IOException e) {
                log.warn("Failed to delete template config file: {}", id, e);
            }
        }

        templateRepository.deleteById(id);
    }

    /**
     * 使用模板
     */
    @Transactional
    public TemplateDTO useTemplate(String id) {
        TeamTemplate template = templateRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Template not found: " + id));

        template.setUsageCount(template.getUsageCount() + 1);
        template = templateRepository.save(template);

        return toDTO(template);
    }

    /**
     * 保存模板配置到 Markdown 文件
     */
    private void saveTemplateConfig(TeamTemplate template) {
        try {
            Path templatesDir = Paths.get(templatesPath).toAbsolutePath();
            Files.createDirectories(templatesDir);

            String filename = template.getName().toLowerCase().replaceAll("[^a-z0-9]", "-") + ".md";
            Path configPath = templatesDir.resolve(filename);

            StringBuilder md = new StringBuilder();
            md.append("# ").append(template.getName()).append("\n\n");
            md.append("> ").append(template.getDescription()).append("\n\n");
            md.append("**Category:** ").append(template.getCategory()).append("\n");
            md.append("**Public:** ").append(template.getIsPublic() ? "Yes" : "No").append("\n");
            md.append("**Usage Count:** ").append(template.getUsageCount()).append("\n\n");

            md.append("## Agents\n\n");
            if (template.getAgents() != null) {
                for (String agentId : template.getAgents()) {
                    md.append("- ").append(agentId).append("\n");
                }
            }

            md.append("\n---\n\n");
            md.append("Created: ").append(template.getCreatedAt()).append("\n");
            md.append("Updated: ").append(template.getUpdatedAt()).append("\n");

            Files.writeString(configPath, md.toString());
            template.setConfigPath(filename);
            templateRepository.save(template);

            log.info("Saved template config: {} to {}", template.getId(), configPath);

        } catch (IOException e) {
            log.error("Failed to save template config: {}", template.getId(), e);
        }
    }

    /**
     * 转换为 DTO
     */
    private TemplateDTO toDTO(TeamTemplate template) {
        return TemplateDTO.builder()
                .id(template.getId())
                .name(template.getName())
                .description(template.getDescription())
                .icon(template.getIcon())
                .category(template.getCategory())
                .agents(template.getAgents())
                .configPath(template.getConfigPath())
                .isPublic(template.getIsPublic())
                .usageCount(template.getUsageCount())
                .createdAt(formatDateTime(template.getCreatedAt()))
                .updatedAt(formatDateTime(template.getUpdatedAt()))
                .build();
    }

    private String formatDateTime(LocalDateTime dateTime) {
        if (dateTime == null) return null;
        return dateTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }
}
