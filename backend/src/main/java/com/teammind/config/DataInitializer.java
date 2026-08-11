package com.teammind.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.teammind.entity.Agent;
import com.teammind.entity.Agent.AgentStatus;
import com.teammind.entity.TeamTemplate;
import com.teammind.entity.User;
import com.teammind.repository.AgentRepository;
import com.teammind.repository.TeamTemplateRepository;
import com.teammind.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 数据初始化器
 * 
 * 应用启动时初始化默认数据
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final AgentRepository agentRepository;
    private final TeamTemplateRepository templateRepository;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Value("${teammind.evolution.enabled:true}")
    private boolean evolutionEnabled;

    @Override
    public void run(String... args) throws Exception {
        log.info("Initializing TeamMind data...");

        // 初始化数据库表结构
        initDatabaseSchema();

        // 初始化默认 Agent
        initDefaultAgents();

        // 初始化默认模板
        initDefaultTemplates();

        // 初始化默认用户（登录/JWT）
        initDefaultUser();

        log.info("TeamMind data initialization completed.");
    }

    /**
     * 初始化数据库表结构
     */
    private void initDatabaseSchema() {
        try {
            ClassPathResource resource = new ClassPathResource("schema.sql");
            String schema = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
            
            // 分割并执行每条 SQL 语句
            String[] statements = schema.split(";");
            for (String statement : statements) {
                statement = statement.trim();
                if (!statement.isEmpty()) {
                    jdbcTemplate.execute(statement);
                }
            }
            log.info("Database schema initialized.");
        } catch (IOException e) {
            log.warn("Could not load schema.sql, using Hibernate auto-ddl: {}", e.getMessage());
        }
    }

    /**
     * 初始化默认 Agent
     */
    private void initDefaultAgents() {
        if (agentRepository.count() > 0) {
            log.info("Agents already exist, skipping initialization.");
            return;
        }

        log.info("Creating default agents...");

        List<Agent> defaultAgents = List.of(
                createAgent("agent-1", "Code Reviewer", "Automatically reviews code for best practices and potential issues", "🔍", "1.0.0", 1234, 4.8, List.of("read:files", "write:comments", "read:repository")),
                createAgent("agent-2", "Task Planner", "Breaks down complex tasks into actionable steps", "📋", "1.2.0", 892, 4.5, List.of("read:tasks", "write:tasks", "read:context")),
                createAgent("agent-3", "Data Analyst", "Analyzes data and generates insights", "📊", "2.0.0", 567, 4.6, List.of("read:data", "write:reports", "read:visualizations")),
                createAgent("agent-4", "Test Engineer", "Automated testing and quality assurance", "🧪", "1.1.0", 423, 4.4, List.of("read:code", "write:tests", "execute:tests")),
                createAgent("agent-5", "Documentation Writer", "Generates and maintains documentation", "📝", "1.0.0", 356, 4.3, List.of("read:code", "write:docs"))
        );

        agentRepository.saveAll(defaultAgents);
        log.info("Created {} default agents.", defaultAgents.size());
    }

    /**
     * 创建 Agent 实体
     */
    private Agent createAgent(String id, String name, String description, String icon, String version, int downloads, double rating, List<String> permissions) {
        return Agent.builder()
                .id(id)
                .name(name)
                .description(description)
                .icon(icon)
                .version(version)
                .author("TeamMind")
                .downloadCount(downloads)
                .rating(rating)
                .status(AgentStatus.IDLE)
                .permissions(permissions)
                .installed(false)
                .enabled(true)
                .evolutionVersion(1)
                .evolutionScore(0.0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    /**
     * 初始化默认模板
     */
    private void initDefaultTemplates() {
        if (templateRepository.count() > 0) {
            log.info("Templates already exist, skipping initialization.");
            return;
        }

        log.info("Creating default templates...");

        List<TeamTemplate> defaultTemplates = List.of(
                createTemplate("template-1", "Code Review Workflow", "Automated code review with multiple agents", "🔍", "Development", List.of("agent-1", "agent-2"), true),
                createTemplate("template-2", "Data Pipeline", "Extract, transform, and load data", "🔄", "Data", List.of("agent-3"), true),
                createTemplate("template-3", "Documentation Generator", "Generate docs from code comments", "📝", "Documentation", List.of("agent-5"), true),
                createTemplate("template-4", "Full Dev Cycle", "Complete development workflow", "🚀", "Development", List.of("agent-1", "agent-2", "agent-4", "agent-5"), true)
        );

        templateRepository.saveAll(defaultTemplates);
        log.info("Created {} default templates.", defaultTemplates.size());
    }

    /**
     * 创建模板实体
     */
    private TeamTemplate createTemplate(String id, String name, String description, String icon, String category, List<String> agents, boolean isPublic) {
        return TeamTemplate.builder()
                .id(id)
                .name(name)
                .description(description)
                .icon(icon)
                .category(category)
                .agents(agents)
                .isPublic(isPublic)
                .usageCount(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    /**
     * 初始化默认用户（登录/JWT 认证）
     *
     * 默认管理员账号：admin，密码由 ${teammind.security.default-admin-password:admin123} 配置。
     * 密码以 BCrypt 哈希存储，绝不保存明文。
     */
    private void initDefaultUser() {
        if (userRepository.count() > 0) {
            log.info("Users already exist, skipping user initialization.");
            return;
        }

        String defaultPassword = "admin123";
        User admin = User.builder()
                .id("user-admin")
                .username("admin")
                .password(passwordEncoder.encode(defaultPassword))
                .email("admin@teammind.local")
                .roles(List.of("ADMIN"))
                .permissions(List.of("agent:evolve", "agent:create", "mission:manage", "read:code", "read:files", "read:web", "write:text"))
                .enabled(true)
                .createdAt(LocalDateTime.now())
                .build();

        userRepository.save(admin);
        log.info("Created default admin user (password stored as BCrypt hash).");
    }
}
