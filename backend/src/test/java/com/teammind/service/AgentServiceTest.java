package com.teammind.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teammind.config.SQLiteWriteLockService;
import com.teammind.dto.AgentDTO;
import com.teammind.dto.CreateAgentRequest;
import com.teammind.entity.Agent;
import com.teammind.entity.Agent.AgentStatus;
import com.teammind.evolution.EvolutionEngine;
import com.teammind.repository.AgentRepository;
import com.teammind.repository.EvolutionRecordRepository;
import com.teammind.websocket.WSEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * AgentService 单元测试
 *
 * 聚焦 Agent 状态机（安装/卸载/启停）与创建、DTO 转换等业务契约。
 */
class AgentServiceTest {

    @TempDir
    Path tempDir;

    private AgentRepository agentRepository;
    private EvolutionRecordRepository evolutionRecordRepository;
    private EvolutionEngine evolutionEngine;
    private WSEventPublisher eventPublisher;
    private SQLiteWriteLockService writeLockService;
    private AgentMetricsService agentMetricsService;
    private AgentService service;

    @BeforeEach
    void setUp() {
        agentRepository = mock(AgentRepository.class);
        evolutionRecordRepository = mock(EvolutionRecordRepository.class);
        evolutionEngine = mock(EvolutionEngine.class);
        eventPublisher = mock(WSEventPublisher.class);
        writeLockService = mock(SQLiteWriteLockService.class);
        agentMetricsService = mock(AgentMetricsService.class);

        service = new AgentService(
                agentRepository,
                evolutionRecordRepository,
                evolutionEngine,
                eventPublisher,
                new ObjectMapper(),
                writeLockService,
                agentMetricsService
        );

        // 将 Markdown 配置写入临时目录，避免污染用户目录
        ReflectionTestUtils.setField(service, "agentsPath", tempDir.toString());

        when(writeLockService.executeWithLock(any(SQLiteWriteLockService.WriteOperation.class)))
                .thenAnswer(inv -> ((SQLiteWriteLockService.WriteOperation<?>) inv.getArgument(0)).execute());
    }

    private Agent buildAgent() {
        return Agent.builder()
                .id("a-1")
                .name("Reviewer")
                .description("Code reviewer")
                .icon("🤖")
                .version("1.0.0")
                .author("User")
                .status(AgentStatus.IDLE)
                .downloadCount(0)
                .permissions(List.of("read:code"))
                .currentPrompt("You are a reviewer.")
                .originalPrompt("You are a reviewer.")
                .tools(List.of())
                .evolutionVersion(1)
                .evolutionScore(0.0)
                .installed(false)
                .enabled(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("安装 Agent 应置为 installed+enabled 并累加下载数")
    void installAgent_marksInstalledAndIncrementsDownload() {
        Agent agent = buildAgent();
        when(agentRepository.findById("a-1")).thenReturn(Optional.of(agent));
        when(agentRepository.save(any(Agent.class))).thenAnswer(inv -> inv.getArgument(0));

        AgentDTO dto = service.installAgent("a-1");
        assertTrue(dto.getInstalled());
        assertTrue(dto.getEnabled());
        assertEquals(1, dto.getDownloadCount());
    }

    @Test
    @DisplayName("重复安装已安装的 Agent 应抛异常")
    void installAgent_alreadyInstalled_throws() {
        Agent agent = buildAgent();
        agent.setInstalled(true);
        when(agentRepository.findById("a-1")).thenReturn(Optional.of(agent));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.installAgent("a-1"));
        assertTrue(ex.getMessage().contains("already installed"));
    }

    @Test
    @DisplayName("卸载 Agent 应置为未安装且禁用")
    void uninstallAgent_marksUninstalledAndDisabled() {
        Agent agent = buildAgent();
        agent.setInstalled(true);
        agent.setEnabled(true);
        when(agentRepository.findById("a-1")).thenReturn(Optional.of(agent));
        when(agentRepository.save(any(Agent.class))).thenAnswer(inv -> inv.getArgument(0));

        service.uninstallAgent("a-1");

        // 通过 repository 捕获保存的 Agent 校验状态
        org.mockito.ArgumentCaptor<Agent> captor =
                org.mockito.ArgumentCaptor.forClass(Agent.class);
        verify(agentRepository).save(captor.capture());
        assertFalse(captor.getValue().getInstalled());
        assertFalse(captor.getValue().getEnabled());
    }

    @Test
    @DisplayName("切换 Agent 启用状态应反映到保存结果")
    void toggleAgent_setsEnabledFlag() {
        Agent agent = buildAgent();
        when(agentRepository.findById("a-1")).thenReturn(Optional.of(agent));
        when(agentRepository.save(any(Agent.class))).thenAnswer(inv -> inv.getArgument(0));

        AgentDTO dto = service.toggleAgent("a-1", false);
        assertFalse(dto.getEnabled());

        AgentDTO dto2 = service.toggleAgent("a-1", true);
        assertTrue(dto2.getEnabled());
    }

    @Test
    @DisplayName("创建 Agent 应初始化为 IDLE、已安装且版本为 1.0.0")
    void createAgent_initializesDefaults() {
        CreateAgentRequest request = CreateAgentRequest.builder()
                .name("New Agent")
                .description("desc")
                .permissions(List.of("read:web"))
                .build();
        when(agentRepository.save(any(Agent.class))).thenAnswer(inv -> inv.getArgument(0));

        AgentDTO dto = service.createAgent(request);
        assertNotNull(dto.getId());
        assertEquals("New Agent", dto.getName());
        assertEquals("idle", dto.getStatus());
        assertEquals("1.0.0", dto.getVersion());
        assertEquals(1, dto.getEvolutionVersion());
        assertTrue(dto.getInstalled());
        assertTrue(dto.getEnabled());
        assertEquals(List.of("read:web"), dto.getPermissions());
    }

    @Test
    @DisplayName("获取 Agent 详情应返回 DTO")
    void getAgent_returnsDTO() {
        Agent agent = buildAgent();
        when(agentRepository.findById("a-1")).thenReturn(Optional.of(agent));

        AgentDTO dto = service.getAgent("a-1");
        assertEquals("a-1", dto.getId());
        assertEquals("Reviewer", dto.getName());
    }

    @Test
    @DisplayName("获取不存在的 Agent 应抛异常")
    void getAgent_notFound_throws() {
        when(agentRepository.findById("missing")).thenReturn(Optional.empty());
        assertThrows(RuntimeException.class, () -> service.getAgent("missing"));
    }

    @Test
    @DisplayName("listAgents 应返回全部 Agent 的 DTO")
    void listAgents_returnsAll() {
        when(agentRepository.findAll()).thenReturn(List.of(buildAgent(), buildAgent()));

        List<AgentDTO> dtos = service.listAgents();
        assertEquals(2, dtos.size());
    }

    @Test
    @DisplayName("listInstalledAgents 仅返回已安装 Agent")
    void listInstalledAgents_filtersInstalled() {
        when(agentRepository.findByInstalledTrue()).thenReturn(List.of(buildAgent()));
        List<AgentDTO> dtos = service.listInstalledAgents();
        assertEquals(1, dtos.size());
        verify(agentRepository).findByInstalledTrue();
    }

    @Test
    @DisplayName("用户评分应委托给 AgentMetricsService 并返回 DTO")
    void rateAgent_delegatesToMetrics() {
        Agent agent = buildAgent();
        when(agentMetricsService.rateAgent("a-1", 4.5)).thenReturn(agent);

        AgentDTO dto = service.rateAgent("a-1", 4.5);
        assertEquals("a-1", dto.getId());
        verify(agentMetricsService).rateAgent("a-1", 4.5);
    }

    @Test
    @DisplayName("获取指标应委托给 AgentMetricsService")
    void getAgentMetrics_delegatesToMetrics() {
        when(agentMetricsService.getAgentMetrics("a-1")).thenReturn(java.util.Map.of("totalMissions", 3));

        java.util.Map<String, Object> metrics = service.getAgentMetrics("a-1");
        assertEquals(3, metrics.get("totalMissions"));
        verify(agentMetricsService).getAgentMetrics("a-1");
    }
}
