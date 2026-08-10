package com.teammind.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teammind.entity.Agent;
import com.teammind.entity.Mission;
import com.teammind.llm.LLMTrackingService;
import com.teammind.repository.AgentRepository;
import com.teammind.repository.MissionRepository;
import com.teammind.websocket.WSEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

/**
 * Mission 运行时管理器
 * 
 * 负责协调多 Agent 执行，管理任务生命周期
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MissionRuntimeManager {

    private final MissionRepository missionRepository;
    private final AgentRepository agentRepository;
    private final AgentExecutionEngine executionEngine;
    private final LLMTrackingService trackingService;
    private final WSEventPublisher eventPublisher;

    // 运行中的任务
    private final Map<String, MissionRuntime> activeMissions = new ConcurrentHashMap<>();
    
    // ✅ 修复：使用有界线程池而非无限制的 CachedThreadPool
    private final ExecutorService executorService = new ThreadPoolExecutor(
        Math.max(4, Runtime.getRuntime().availableProcessors()),  // corePoolSize
        Math.max(8, Runtime.getRuntime().availableProcessors() * 2),  // maxPoolSize
        60,                                                         // keepAliveTime
        TimeUnit.SECONDS,
        new LinkedBlockingQueue<>(100),                            // 有界队列，防止 OOM
        new ThreadFactory() {
            private final java.util.concurrent.atomic.AtomicInteger count = 
                new java.util.concurrent.atomic.AtomicInteger(0);
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r);
                t.setName("mission-executor-" + count.incrementAndGet());
                t.setDaemon(false);
                return t;
            }
        },
        new ThreadPoolExecutor.CallerRunsPolicy()  // 拒绝策略：调用者线程执行
    );

    /**
     * 启动任务
     */
    @Async
    public void startMission(String missionId) {
        log.info("Starting mission: {}", missionId);

        Mission mission = missionRepository.findById(missionId)
                .orElseThrow(() -> new RuntimeException("Mission not found: " + missionId));

        // 更新状态
        mission.setStatus(Mission.MissionStatus.RUNNING);
        mission.setUpdatedAt(LocalDateTime.now());
        missionRepository.save(mission);

        // 创建运行时
        MissionRuntime runtime = new MissionRuntime(mission);
        activeMissions.put(missionId, runtime);

        // 发布事件
        eventPublisher.publishMissionStarted(missionId);

        try {
            // 执行任务
            executeMission(runtime);

            // ✅ 检查是否被取消
            if (runtime.isCancelled()) {
                log.info("Mission was cancelled: {}", missionId);
                mission.setStatus(Mission.MissionStatus.FAILED);
                mission.setUpdatedAt(LocalDateTime.now());
                missionRepository.save(mission);
                addLog(mission, "warning", null, "Mission was cancelled by user");
                eventPublisher.publishMissionFailed(missionId);
                return;
            }

            // 标记完成
            mission.setStatus(Mission.MissionStatus.COMPLETED);
            mission.setCompletedAt(LocalDateTime.now());
            mission.setUpdatedAt(LocalDateTime.now());

            // 设置结果
            Map<String, Object> result = buildMissionResult(runtime);
            mission.setResult(result);

            missionRepository.save(mission);

            // 发布完成事件
            eventPublisher.publishMissionCompleted(missionId, result);

            log.info("Mission completed: {}", missionId);

        } catch (Exception e) {
            log.error("Mission failed: {}", missionId, e);

            mission.setStatus(Mission.MissionStatus.FAILED);
            mission.setUpdatedAt(LocalDateTime.now());
            missionRepository.save(mission);

            // 添加错误日志
            addLog(mission, "error", null, "Mission failed: " + e.getMessage());

            // ✅ 发布任务失败事件
            eventPublisher.publishMissionFailed(missionId, e.getMessage());

        } finally {
            activeMissions.remove(missionId);
        }
    }

    /**
     * 执行任务
     */
    private void executeMission(MissionRuntime runtime) {
        Mission mission = runtime.getMission();

        // 解析任务图
        List<Map<String, Object>> nodes = mission.getNodes();
        List<Map<String, Object>> edges = mission.getEdges();

        if (nodes == null || nodes.isEmpty()) {
            // 没有预定义的节点，使用简单执行
            executeSimpleMission(runtime);
            return;
        }

        // ✅ 切换为并行执行：使用有界线程池并行执行独立节点
        executeWithTopologyParallel(runtime, nodes, edges);
    }

    /**
     * 简单任务执行
     */
    private void executeSimpleMission(MissionRuntime runtime) {
        Mission mission = runtime.getMission();

        log.info("Executing simple mission: {}", mission.getId());

        // ✅ 检查暂停/取消状态
        if (runtime.isCancelled()) {
            log.info("Mission cancelled before execution: {}", mission.getId());
            return;
        }
        while (runtime.isPaused() && !runtime.isCancelled()) {
            log.debug("Mission paused, waiting: {}", mission.getId());
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        if (runtime.isCancelled()) {
            log.info("Mission cancelled while paused: {}", mission.getId());
            return;
        }

        // 使用默认 Agent
        List<Agent> agents = agentRepository.findByInstalledTrueAndEnabledTrue();
        if (agents.isEmpty()) {
            throw new RuntimeException("No available agents");
        }

        Agent primaryAgent = agents.get(0);

        // 创建执行上下文
        AgentExecutionContext context = AgentExecutionContext.builder()
                .executionId(UUID.randomUUID().toString())
                .agentId(primaryAgent.getId())
                .agentName(primaryAgent.getName())
                .missionId(mission.getId())
                .userRequest(mission.getDescription())
                .createdAt(LocalDateTime.now())
                .build();

        // 执行（带超时保护 + 取消传播）
        CompletableFuture<AgentExecutionResult> execFuture = executionEngine.execute(context);
        AgentExecutionResult result;
        try {
            result = execFuture.get(context.getTimeoutMs(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            execFuture.cancel(true);
            log.error("Simple mission execution timeout: {}", mission.getId());
            result = AgentExecutionResult.failure(context.getExecutionId(), primaryAgent.getId(),
                    "Execution timeout after " + context.getTimeoutMs() + "ms");
        } catch (InterruptedException e) {
            execFuture.cancel(true);
            Thread.currentThread().interrupt();
            log.error("Simple mission execution interrupted: {}", mission.getId());
            result = AgentExecutionResult.failure(context.getExecutionId(), primaryAgent.getId(),
                    "Execution interrupted");
        } catch (ExecutionException e) {
            log.error("Simple mission execution failed: {}", mission.getId(), e.getCause());
            result = AgentExecutionResult.failure(context.getExecutionId(), primaryAgent.getId(),
                    "Execution failed: " + e.getCause().getMessage());
        }

        // 记录结果
        runtime.addResult(primaryAgent.getId(), result);

        // 添加日志
        addLog(mission, "task", primaryAgent.getId(), 
                result.getResponse() != null ? result.getResponse() : result.getError());
    }

    /**
     * 按拓扑结构执行
     */
    private void executeWithTopology(MissionRuntime runtime, 
                                     List<Map<String, Object>> nodes,
                                     List<Map<String, Object>> edges) {
        Mission mission = runtime.getMission();

        log.info("Executing mission with topology: {} nodes, {} edges", 
                nodes.size(), edges != null ? edges.size() : 0);

        // 构建依赖图
        Map<String, Set<String>> dependencies = buildDependencyGraph(nodes, edges);

        // 拓扑排序
        List<String> executionOrder = topologicalSort(nodes, dependencies);

        // 按顺序执行节点
        Map<String, AgentExecutionResult> results = new ConcurrentHashMap<>();

        for (String nodeId : executionOrder) {
            // ✅ 检查暂停/取消状态
            if (runtime.isCancelled()) {
                log.info("Mission cancelled, stopping execution: {}", mission.getId());
                addLog(mission, "warning", null, "Mission cancelled by user");
                break;
            }

            // 如果暂停，等待恢复或取消
            while (runtime.isPaused() && !runtime.isCancelled()) {
                log.debug("Mission paused, waiting to resume: {}", mission.getId());
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }

            if (runtime.isCancelled()) {
                log.info("Mission cancelled while paused, stopping: {}", mission.getId());
                addLog(mission, "warning", null, "Mission cancelled by user");
                break;
            }

            // 找到节点
            Map<String, Object> node = nodes.stream()
                    .filter(n -> nodeId.equals(n.get("id")))
                    .findFirst()
                    .orElse(null);

            if (node == null) continue;

            String nodeType = (String) node.get("type");
            Map<String, Object> nodeData = (Map<String, Object>) node.get("data");

            // 更新节点状态
            updateNodeStatus(mission, nodeId, "running");
            publishNodeUpdate(mission.getId(), nodeId, "running");

            try {
                if ("agent".equals(nodeType)) {
                    // 执行 Agent 节点
                    String agentId = nodeData != null ? (String) nodeData.get("agentId") : null;
                    if (agentId == null) continue;

                    // 收集依赖输出
                    Map<String, Object> depOutputs = collectDependencyOutputs(nodeId, dependencies, results);

                    // 创建上下文
                    AgentExecutionContext context = AgentExecutionContext.builder()
                            .executionId(UUID.randomUUID().toString())
                            .agentId(agentId)
                            .missionId(mission.getId())
                            .nodeId(nodeId)
                            .userRequest(mission.getDescription())
                            .dependencies(depOutputs)
                            .createdAt(LocalDateTime.now())
                            .build();

                    // ✅ 修复：添加超时保护 + 取消传播
                    AgentExecutionResult result;
                    CompletableFuture<AgentExecutionResult> execFuture = executionEngine.execute(context);
                    try {
                        result = execFuture.get(context.getTimeoutMs(), TimeUnit.MILLISECONDS);
                    } catch (TimeoutException e) {
                        log.error("Node execution timeout: {} ({}ms)", nodeId, context.getTimeoutMs());
                        // ✅ 取消传播：取消执行 Future
                        execFuture.cancel(true);
                        result = AgentExecutionResult.builder()
                            .executionId(context.getExecutionId())
                            .agentId(context.getAgentId())
                            .success(false)
                            .status(AgentExecutionResult.ExecutionStatus.TIMEOUT)
                            .error("Execution timeout after " + context.getTimeoutMs() + "ms")
                            .finishReason("timeout")
                            .completedAt(LocalDateTime.now())
                            .build();
                        updateNodeStatus(mission, nodeId, "timeout");
                        publishNodeUpdate(mission.getId(), nodeId, "timeout");
                    } catch (InterruptedException e) {
                        log.error("Node execution interrupted: {}", nodeId);
                        Thread.currentThread().interrupt();
                        // ✅ 取消传播：取消执行 Future
                        execFuture.cancel(true);
                        result = AgentExecutionResult.builder()
                            .executionId(context.getExecutionId())
                            .agentId(context.getAgentId())
                            .success(false)
                            .status(AgentExecutionResult.ExecutionStatus.TIMEOUT)
                            .error("Execution interrupted")
                            .finishReason("interrupted")
                            .completedAt(LocalDateTime.now())
                            .build();
                        updateNodeStatus(mission, nodeId, "error");
                    } catch (ExecutionException e) {
                        log.error("Node execution failed: {}", nodeId, e.getCause());
                        result = AgentExecutionResult.builder()
                            .executionId(context.getExecutionId())
                            .agentId(context.getAgentId())
                            .success(false)
                            .status(AgentExecutionResult.ExecutionStatus.FAILED)
                            .error("Execution failed: " + e.getCause().getMessage())
                            .finishReason("error")
                            .completedAt(LocalDateTime.now())
                            .build();
                        updateNodeStatus(mission, nodeId, "error");
                    }
                    
                    results.put(nodeId, result);
                    runtime.addResult(agentId, result);

                    // 更新节点状态
                    if (!result.getStatus().equals(AgentExecutionResult.ExecutionStatus.TIMEOUT)) {
                        String newStatus = result.isSuccess() ? "success" : "error";
                        updateNodeStatus(mission, nodeId, newStatus);
                        updateNodeOutput(mission, nodeId, result.getOutput());
                        publishNodeUpdate(mission.getId(), nodeId, newStatus);
                    }

                    // 添加日志
                    addLog(mission, "task", agentId, 
                            result.getResponse() != null ? result.getResponse() : result.getError());

                } else if ("input".equals(nodeType)) {
                    // 输入节点 - 直接使用 mission 输入
                    results.put(nodeId, AgentExecutionResult.success(
                            UUID.randomUUID().toString(), 
                            "input", 
                            mission.getDescription()
                    ));

                } else if ("output".equals(nodeType)) {
                    // 输出节点 - 收集最终结果
                    Map<String, Object> finalOutput = collectDependencyOutputs(nodeId, dependencies, results);
                    mission.setResult(finalOutput);
                    updateNodeStatus(mission, nodeId, "success");

                } else {
                    // 其他类型节点
                    log.warn("Unknown node type: {}", nodeType);
                }

            } catch (Exception e) {
                log.error("Node execution failed: {}", nodeId, e);
                updateNodeStatus(mission, nodeId, "error");
                publishNodeUpdate(mission.getId(), nodeId, "error");
            }
        }
    }

    /**
     * 构建依赖图
     */
    private Map<String, Set<String>> buildDependencyGraph(List<Map<String, Object>> nodes,
                                                           List<Map<String, Object>> edges) {
        Map<String, Set<String>> deps = new HashMap<>();

        for (Map<String, Object> node : nodes) {
            deps.put((String) node.get("id"), new HashSet<>());
        }

        if (edges != null) {
            for (Map<String, Object> edge : edges) {
                String target = (String) edge.get("target");
                String source = (String) edge.get("source");
                if (target != null && source != null) {
                    deps.computeIfAbsent(target, k -> new HashSet<>()).add(source);
                }
            }
        }

        return deps;
    }

    /**
     * 拓扑排序
     */
    private List<String> topologicalSort(List<Map<String, Object>> nodes,
                                          Map<String, Set<String>> dependencies) {
        List<String> result = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Set<String> visiting = new HashSet<>();

        for (Map<String, Object> node : nodes) {
            String nodeId = (String) node.get("id");
            if (!visited.contains(nodeId)) {
                visit(nodeId, dependencies, visited, visiting, result);
            }
        }

        return result;
    }

    private void visit(String nodeId, Map<String, Set<String>> dependencies,
                       Set<String> visited, Set<String> visiting, List<String> result) {
        if (visiting.contains(nodeId)) {
            return; // 循环依赖，跳过
        }
        if (visited.contains(nodeId)) {
            return;
        }

        visiting.add(nodeId);

        for (String dep : dependencies.getOrDefault(nodeId, Collections.emptySet())) {
            visit(dep, dependencies, visited, visiting, result);
        }

        visiting.remove(nodeId);
        visited.add(nodeId);
        result.add(nodeId);
    }

    /**
     * 收集依赖输出
     */
    private Map<String, Object> collectDependencyOutputs(String nodeId,
                                                          Map<String, Set<String>> dependencies,
                                                          Map<String, AgentExecutionResult> results) {
        Map<String, Object> outputs = new HashMap<>();

        Set<String> deps = dependencies.getOrDefault(nodeId, Collections.emptySet());
        for (String depId : deps) {
            AgentExecutionResult result = results.get(depId);
            if (result != null && result.getOutput() != null) {
                outputs.put(depId, result.getOutput());
            }
            if (result != null && result.getResponse() != null) {
                outputs.put(depId + "_response", result.getResponse());
            }
        }

        return outputs;
    }

    /**
     * ✅ 新增：按拓扑结构并行执行
     * 独立的节点可以并行执行，提升性能
     */
    private void executeWithTopologyParallel(MissionRuntime runtime, 
                                             List<Map<String, Object>> nodes,
                                             List<Map<String, Object>> edges) {
        Mission mission = runtime.getMission();

        log.info("Executing mission with parallel topology: {} nodes, {} edges", 
                nodes.size(), edges != null ? edges.size() : 0);

        // 构建依赖图和反向依赖图
        Map<String, Set<String>> dependencies = buildDependencyGraph(nodes, edges);
        Map<String, Set<String>> dependents = buildDependentGraph(nodes, edges);

        // 计算入度（每个节点有多少个依赖）
        Map<String, Integer> inDegree = new HashMap<>();
        for (Map<String, Object> node : nodes) {
            String nodeId = (String) node.get("id");
            inDegree.put(nodeId, dependencies.getOrDefault(nodeId, new HashSet<>()).size());
        }

        // 找到所有入度为 0 的节点（可以立即执行）
        Queue<String> readyNodes = new ConcurrentLinkedQueue<>();
        for (String nodeId : inDegree.keySet()) {
            if (inDegree.get(nodeId) == 0) {
                readyNodes.offer(nodeId);
            }
        }

        // 执行结果和 Future
        Map<String, AgentExecutionResult> results = new ConcurrentHashMap<>();
        Map<String, CompletableFuture<AgentExecutionResult>> futures = new ConcurrentHashMap<>();
        // ✅ 新增：跟踪内部执行 Future 以便取消传播
        Map<String, CompletableFuture<AgentExecutionResult>> innerFutures = new ConcurrentHashMap<>();

        // 执行就绪的节点
        while (!readyNodes.isEmpty()) {
            // ✅ 检查暂停/取消状态
            if (runtime.isCancelled()) {
                log.info("Mission cancelled, stopping parallel execution: {}", mission.getId());
                addLog(mission, "warning", null, "Mission cancelled by user");
                // ✅ 取消传播：取消所有进行中的执行 Future
                futures.values().forEach(f -> f.cancel(true));
                break;
            }

            // 如果暂停，等待恢复或取消
            while (runtime.isPaused() && !runtime.isCancelled()) {
                log.debug("Mission paused, waiting to resume: {}", mission.getId());
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }

            if (runtime.isCancelled()) {
                log.info("Mission cancelled while paused, stopping: {}", mission.getId());
                addLog(mission, "warning", null, "Mission cancelled by user");
                futures.values().forEach(f -> f.cancel(true));
                break;
            }

            String nodeId = readyNodes.poll();

            // 找到节点
            Map<String, Object> node = nodes.stream()
                    .filter(n -> nodeId.equals(n.get("id")))
                    .findFirst()
                    .orElse(null);

            if (node == null) continue;

            String nodeType = (String) node.get("type");
            Map<String, Object> nodeData = (Map<String, Object>) node.get("data");

            // 更新节点状态
            updateNodeStatus(mission, nodeId, "running");
            publishNodeUpdate(mission.getId(), nodeId, "running");

            // 异步执行节点
            CompletableFuture<AgentExecutionResult> future = CompletableFuture.supplyAsync(() -> {
                try {
                    if ("agent".equals(nodeType)) {
                        String agentId = nodeData != null ? (String) nodeData.get("agentId") : null;
                        if (agentId == null) {
                            return AgentExecutionResult.failure(UUID.randomUUID().toString(), "unknown", "Agent ID not found");
                        }

                        Map<String, Object> depOutputs = collectDependencyOutputs(nodeId, dependencies, results);
                        AgentExecutionContext context = AgentExecutionContext.builder()
                                .executionId(UUID.randomUUID().toString())
                                .agentId(agentId)
                                .missionId(mission.getId())
                                .nodeId(nodeId)
                                .userRequest(mission.getDescription())
                                .dependencies(depOutputs)
                                .createdAt(LocalDateTime.now())
                                .build();

                        try {
                            // ✅ 存储内部执行 Future 以便取消传播
                            CompletableFuture<AgentExecutionResult> inner = executionEngine.execute(context);
                            innerFutures.put(nodeId, inner);
                            try {
                                return inner.get(context.getTimeoutMs(), TimeUnit.MILLISECONDS);
                            } finally {
                                innerFutures.remove(nodeId);
                            }
                        } catch (TimeoutException e) {
                            log.error("Node execution timeout: {}", nodeId);
                            return AgentExecutionResult.builder()
                                .executionId(context.getExecutionId())
                                .agentId(context.getAgentId())
                                .success(false)
                                .status(AgentExecutionResult.ExecutionStatus.TIMEOUT)
                                .error("Timeout after " + context.getTimeoutMs() + "ms")
                                .finishReason("timeout")
                                .completedAt(LocalDateTime.now())
                                .build();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return AgentExecutionResult.builder()
                                .executionId(context.getExecutionId())
                                .agentId(context.getAgentId())
                                .success(false)
                                .status(AgentExecutionResult.ExecutionStatus.TIMEOUT)
                                .error("Interrupted")
                                .finishReason("interrupted")
                                .completedAt(LocalDateTime.now())
                                .build();
                        } catch (ExecutionException e) {
                            return AgentExecutionResult.builder()
                                .executionId(context.getExecutionId())
                                .agentId(context.getAgentId())
                                .success(false)
                                .status(AgentExecutionResult.ExecutionStatus.FAILED)
                                .error("Failed: " + e.getCause().getMessage())
                                .finishReason("error")
                                .completedAt(LocalDateTime.now())
                                .build();
                        }

                    } else if ("input".equals(nodeType)) {
                        return AgentExecutionResult.success(UUID.randomUUID().toString(), "input", mission.getDescription());
                    } else if ("output".equals(nodeType)) {
                        Map<String, Object> finalOutput = collectDependencyOutputs(nodeId, dependencies, results);
                        mission.setResult(finalOutput);
                        // 将 Map 转为 JSON 字符串作为 response
                        try {
                            String outputJson = new ObjectMapper().writeValueAsString(finalOutput);
                            return AgentExecutionResult.success(UUID.randomUUID().toString(), "output", outputJson);
                        } catch (Exception ex) {
                            return AgentExecutionResult.success(UUID.randomUUID().toString(), "output",
                                    finalOutput != null ? finalOutput.toString() : "");
                        }
                    } else {
                        log.warn("Unknown node type: {}", nodeType);
                        return AgentExecutionResult.failure(UUID.randomUUID().toString(), "unknown", "Unknown type");
                    }

                } catch (Exception e) {
                    log.error("Node execution failed: {}", nodeId, e);
                    return AgentExecutionResult.failure(UUID.randomUUID().toString(), "unknown", e.getMessage());
                }
            }, executorService);

            futures.put(nodeId, future);

            // 当节点完成时，检查其依赖节点
            future.thenAccept(result -> {
                // ✅ 检查取消状态：如果任务已取消，不再继续调度后续节点
                if (runtime.isCancelled()) {
                    log.debug("Mission cancelled, skipping dependent scheduling for node: {}", nodeId);
                    return;
                }

                // ✅ 如果暂停，等待恢复或取消后再调度依赖节点
                while (runtime.isPaused() && !runtime.isCancelled()) {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
                if (runtime.isCancelled()) {
                    return;
                }

                results.put(nodeId, result);
                String newStatus = result.isSuccess() ? "success" : "error";
                updateNodeStatus(mission, nodeId, newStatus);
                updateNodeOutput(mission, nodeId, result.getOutput());
                publishNodeUpdate(mission.getId(), nodeId, newStatus);

                if (result.getAgentId() != null) {
                    addLog(mission, "task", result.getAgentId(), 
                            result.getResponse() != null ? result.getResponse() : result.getError());
                }

                // 减少依赖节点的入度
                for (String dependent : dependents.getOrDefault(nodeId, new HashSet<>())) {
                    int newInDegree = inDegree.get(dependent) - 1;
                    inDegree.put(dependent, newInDegree);

                    if (newInDegree == 0 && !runtime.isCancelled()) {
                        readyNodes.offer(dependent);
                    }
                }
            });
        }

        // 等待所有节点完成（同时检查暂停/取消状态）
        while (!futures.isEmpty()) {
            // ✅ 检查取消状态
            if (runtime.isCancelled()) {
                log.info("Mission cancelled during execution wait: {}", mission.getId());
                futures.values().forEach(f -> f.cancel(true));
                innerFutures.values().forEach(f -> f.cancel(true));
                break;
            }

            // 如果暂停，等待恢复或取消
            while (runtime.isPaused() && !runtime.isCancelled() && !futures.isEmpty()) {
                log.debug("Mission paused during execution wait: {}", mission.getId());
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }

            try {
                CompletableFuture.allOf(futures.values().toArray(new CompletableFuture[0]))
                    .get(500, TimeUnit.MILLISECONDS);
                break; // 全部完成
            } catch (TimeoutException e) {
                // 超时，继续检查暂停/取消
                continue;
            } catch (InterruptedException e) {
                log.error("Mission execution interrupted");
                Thread.currentThread().interrupt();
                break;
            } catch (ExecutionException e) {
                log.error("Mission execution failed", e.getCause());
                break;
            }
        }

        // 最终检查：如果执行超时（5分钟）
        if (!runtime.isCancelled() && !futures.isEmpty()) {
            try {
                CompletableFuture.allOf(futures.values().toArray(new CompletableFuture[0]))
                    .get(1, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.error("Mission execution timeout");
                mission.setStatus(Mission.MissionStatus.FAILED);
                addLog(mission, "error", null, "Mission execution timeout");
                futures.values().forEach(f -> f.cancel(true));
            }
        }
    }

    /**
     * ✅ 新增：构建反向依赖图
     */
    private Map<String, Set<String>> buildDependentGraph(List<Map<String, Object>> nodes,
                                                          List<Map<String, Object>> edges) {
        Map<String, Set<String>> dependents = new HashMap<>();

        for (Map<String, Object> node : nodes) {
            dependents.put((String) node.get("id"), new HashSet<>());
        }

        if (edges != null) {
            for (Map<String, Object> edge : edges) {
                String source = (String) edge.get("source");
                String target = (String) edge.get("target");
                if (source != null && target != null) {
                    dependents.computeIfAbsent(source, k -> new HashSet<>()).add(target);
                }
            }
        }

        return dependents;
    }

    /**
     * 更新节点状态
     */
    private void updateNodeStatus(Mission mission, String nodeId, String status) {
        List<Map<String, Object>> nodes = mission.getNodes();
        if (nodes == null) return;

        for (Map<String, Object> node : nodes) {
            if (nodeId.equals(node.get("id"))) {
                Map<String, Object> data = (Map<String, Object>) node.get("data");
                if (data == null) {
                    data = new HashMap<>();
                    node.put("data", data);
                }
                data.put("status", status);
                break;
            }
        }

        mission.setNodes(nodes);
        mission.setUpdatedAt(LocalDateTime.now());
    }

    /**
     * 更新节点输出
     */
    private void updateNodeOutput(Mission mission, String nodeId, Map<String, Object> output) {
        List<Map<String, Object>> nodes = mission.getNodes();
        if (nodes == null) return;

        for (Map<String, Object> node : nodes) {
            if (nodeId.equals(node.get("id"))) {
                Map<String, Object> data = (Map<String, Object>) node.get("data");
                if (data == null) {
                    data = new HashMap<>();
                    node.put("data", data);
                }
                data.put("output", output);
                break;
            }
        }

        mission.setNodes(nodes);
    }

    /**
     * 发布节点更新
     */
    private void publishNodeUpdate(String missionId, String nodeId, String status) {
        eventPublisher.publishNodeUpdate(missionId, nodeId, Map.of("status", status));
    }

    /**
     * 添加日志
     */
    private void addLog(Mission mission, String type, String agentId, String message) {
        List<Map<String, Object>> logs = mission.getLogs();
        if (logs == null) {
            logs = new ArrayList<>();
        }

        Map<String, Object> log = new HashMap<>();
        log.put("id", UUID.randomUUID().toString());
        log.put("type", type);
        log.put("timestamp", LocalDateTime.now().toString());
        log.put("agentId", agentId);
        log.put("message", message);
        logs.add(log);

        mission.setLogs(logs);

        // 发布日志事件
        eventPublisher.publishLog(mission.getId(), type, agentId, message);
    }

    /**
     * 构建任务结果
     */
    private Map<String, Object> buildMissionResult(MissionRuntime runtime) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("completedAt", LocalDateTime.now().toString());

        // 收集所有 Agent 结果
        Map<String, AgentExecutionResult> agentResults = runtime.getResults();
        Map<String, Object> outputs = new HashMap<>();
        agentResults.forEach((agentId, execResult) -> {
            outputs.put(agentId, Map.of(
                    "success", execResult.isSuccess(),
                    "response", execResult.getResponse(),
                    "tokens", execResult.getTokenUsage() != null ? 
                            execResult.getTokenUsage().getTotalTokens() : 0
            ));
        });
        result.put("agentOutputs", outputs);

        return result;
    }

    /**
     * 暂停任务
     */
    public void pauseMission(String missionId) {
        MissionRuntime runtime = activeMissions.get(missionId);
        if (runtime != null) {
            runtime.pause();
        }
    }

    /**
     * 恢复任务
     */
    public void resumeMission(String missionId) {
        MissionRuntime runtime = activeMissions.get(missionId);
        if (runtime != null) {
            runtime.resume();
        }
    }

    /**
     * 取消任务
     */
    public void cancelMission(String missionId) {
        MissionRuntime runtime = activeMissions.get(missionId);
        if (runtime != null) {
            runtime.cancel();
            activeMissions.remove(missionId);
        }
    }

    /**
     * 获取任务状态
     */
    public Optional<MissionRuntime> getMissionRuntime(String missionId) {
        return Optional.ofNullable(activeMissions.get(missionId));
    }

    /**
     * 任务运行时
     */
    @lombok.Data
    public static class MissionRuntime {
        private final Mission mission;
        private final Map<String, AgentExecutionResult> results = new ConcurrentHashMap<>();
        private volatile boolean paused = false;
        private volatile boolean cancelled = false;

        public MissionRuntime(Mission mission) {
            this.mission = mission;
        }

        public void addResult(String agentId, AgentExecutionResult result) {
            results.put(agentId, result);
        }

        public void pause() {
            paused = true;
        }

        public void resume() {
            paused = false;
        }

        public void cancel() {
            cancelled = true;
        }

        public boolean isPaused() {
            return paused;
        }

        public boolean isCancelled() {
            return cancelled;
        }
    }
}
