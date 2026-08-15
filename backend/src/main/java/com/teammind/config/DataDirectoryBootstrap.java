package com.teammind.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * 数据目录引导器
 *
 * 在 Spring 启动最早阶段（EnvironmentPostProcessor）确保
 * {@code teammind.data-path} / {@code teammind.agents-path} / {@code teammind.templates-path}
 * 三类目录存在并写入环境变量。
 *
 * 背景：SQLite JDBC driver 不会自动创建父目录。Spring Boot 在 DataSource 初始化阶段就需要
 * SQLite 连接，而 {@code CommandLineRunner}（{@link DataInitializer}）运行时机晚于 DataSource，
 * 因此数据目录必须在此之前创建完成。
 *
 * 通过 {@code META-INF/spring.factories}（兼容 Spring Boot 2）/ {@code spring.factories}（3.x）
 * 自动注册；无需 @Component。
 */
public class DataDirectoryBootstrap implements EnvironmentPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(DataDirectoryBootstrap.class);

    private static final String PROPERTY_SOURCE_NAME = "teammindDataDirectory";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String dataPath = resolveDataPath(environment);
        String agentsPath = resolveAgentsPath(environment, dataPath);
        String templatesPath = resolveTemplatesPath(environment, dataPath);

        try {
            ensureDirectory(dataPath, "data");
            ensureDirectory(agentsPath, "agents");
            ensureDirectory(templatesPath, "templates");
        } catch (IOException e) {
            log.error("Failed to create TeamMind data directories: {}", e.getMessage(), e);
            // 不抛异常：让 Spring Boot 继续启动；启动后 SQLite 报错可被运维人员快速识别。
        }

        // 显式回写路径到环境（确保下游 @Value 解析与 application.yml 一致）
        MutablePropertySources sources = environment.getPropertySources();
        Map<String, Object> overrides = new HashMap<>();
        overrides.put("teammind.data-path", dataPath);
        overrides.put("teammind.agents-path", agentsPath);
        overrides.put("teammind.templates-path", templatesPath);
        PropertySource<?> source = new MapPropertySource(PROPERTY_SOURCE_NAME, overrides);
        // 添加在最高优先级，确保覆盖 application.yml 中的同名 key
        sources.addFirst(source);
    }

    private String resolveDataPath(ConfigurableEnvironment environment) {
        String configured = environment.getProperty("teammind.data-path");
        if (configured != null && !configured.isBlank()) {
            return expandPlaceholders(configured, environment);
        }
        String userHome = System.getProperty("user.home");
        return Paths.get(userHome, ".teammind").toString();
    }

    private String resolveAgentsPath(ConfigurableEnvironment environment, String dataPath) {
        String configured = environment.getProperty("teammind.agents-path");
        if (configured != null && !configured.isBlank()) {
            return expandPlaceholders(configured, environment);
        }
        return Paths.get(dataPath, "agents").toString();
    }

    private String resolveTemplatesPath(ConfigurableEnvironment environment, String dataPath) {
        String configured = environment.getProperty("teammind.templates-path");
        if (configured != null && !configured.isBlank()) {
            return expandPlaceholders(configured, environment);
        }
        return Paths.get(dataPath, "templates").toString();
    }

    /**
     * 解析 ${...} 占位符
     */
    private String expandPlaceholders(String raw, ConfigurableEnvironment environment) {
        return environment.resolvePlaceholders(raw);
    }

    private void ensureDirectory(String path, String label) throws IOException {
        Path dir = Paths.get(path);
        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
            log.info("Created {} directory: {}", label, dir.toAbsolutePath());
        }
    }
}