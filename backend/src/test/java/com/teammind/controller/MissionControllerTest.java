package com.teammind.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teammind.dto.CreateMissionRequest;
import com.teammind.dto.MissionDTO;
import com.teammind.dto.MissionHistoryDTO;
import com.teammind.dto.PaginatedResponse;
import com.teammind.dto.UpdateMissionRequest;
import com.teammind.service.MissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * MissionController 单元测试
 *
 * 验证 REST 接口契约：URL 路由、HTTP 方法、请求/响应 JSON 结构、
 * 分页参数传递、状态码等。
 */
class MissionControllerTest {

    private MissionService missionService;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        missionService = mock(MissionService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new MissionController(missionService)).build();
    }

    private MissionDTO buildDTO() {
        return MissionDTO.builder()
                .id("m-1")
                .title("Test Mission")
                .status("pending")
                .build();
    }

    @Test
    @DisplayName("POST /api/missions 应创建任务并返回 200 + success")
    void createMission_returnsSuccess() throws Exception {
        when(missionService.createMission(any(CreateMissionRequest.class))).thenReturn(buildDTO());

        mockMvc.perform(post("/api/missions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"New Mission\",\"description\":\"d\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.id", is("m-1")))
                .andExpect(jsonPath("$.data.status", is("pending")));

        verify(missionService).createMission(any(CreateMissionRequest.class));
    }

    @Test
    @DisplayName("GET /api/missions/{id} 应返回任务详情")
    void getMission_returnsDTO() throws Exception {
        when(missionService.getMission("m-1")).thenReturn(buildDTO());

        mockMvc.perform(get("/api/missions/m-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.title", is("Test Mission")));

        verify(missionService).getMission("m-1");
    }

    @Test
    @DisplayName("GET /api/missions 应传递分页参数并返回分页数据")
    void listMissions_passesPagingParams() throws Exception {
        PaginatedResponse<MissionHistoryDTO> page = PaginatedResponse.of(
                List.of(), 0L, 1, 20);
        when(missionService.listMissions(2, 10)).thenReturn(page);

        mockMvc.perform(get("/api/missions").param("page", "2").param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total", is(0)));

        verify(missionService).listMissions(2, 10);
    }

    @Test
    @DisplayName("GET /api/missions 缺省分页参数应使用默认值 1/20")
    void listMissions_defaultPaging() throws Exception {
        PaginatedResponse<MissionHistoryDTO> page = PaginatedResponse.of(
                List.of(), 0L, 1, 20);
        when(missionService.listMissions(1, 20)).thenReturn(page);

        mockMvc.perform(get("/api/missions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page", is(1)));

        verify(missionService).listMissions(1, 20);
    }

    @Test
    @DisplayName("PUT /api/missions/{id} 应更新任务")
    void updateMission_updates() throws Exception {
        MissionDTO updated = buildDTO();
        updated.setTitle("Updated");
        when(missionService.updateMission(eq("m-1"), any(UpdateMissionRequest.class))).thenReturn(updated);

        mockMvc.perform(put("/api/missions/m-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Updated\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title", is("Updated")));
    }

    @Test
    @DisplayName("DELETE /api/missions/{id} 应删除任务")
    void deleteMission_deletes() throws Exception {
        doNothing().when(missionService).deleteMission("m-1");

        mockMvc.perform(delete("/api/missions/m-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));

        verify(missionService).deleteMission("m-1");
    }

    @Test
    @DisplayName("POST /api/missions/{id}/clone 应克隆任务")
    void cloneMission_clones() throws Exception {
        when(missionService.cloneMission("m-1")).thenReturn(buildDTO());

        mockMvc.perform(post("/api/missions/m-1/clone"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));
    }

    @Test
    @DisplayName("POST /api/missions/{id}/start 应启动任务")
    void startMission_starts() throws Exception {
        when(missionService.startMission("m-1")).thenReturn(buildDTO());

        mockMvc.perform(post("/api/missions/m-1/start"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", is("Mission started")));

        verify(missionService).startMission("m-1");
    }

    @Test
    @DisplayName("POST /api/missions/{id}/pause 应暂停任务")
    void pauseMission_pauses() throws Exception {
        when(missionService.pauseMission("m-1")).thenReturn(buildDTO());

        mockMvc.perform(post("/api/missions/m-1/pause"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", is("Mission paused")));
    }

    @Test
    @DisplayName("POST /api/missions/{id}/resume 应恢复任务")
    void resumeMission_resumes() throws Exception {
        when(missionService.resumeMission("m-1")).thenReturn(buildDTO());

        mockMvc.perform(post("/api/missions/m-1/resume"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", is("Mission resumed")));
    }

    @Test
    @DisplayName("POST /api/missions/{id}/cancel 应取消任务")
    void cancelMission_cancels() throws Exception {
        when(missionService.cancelMission("m-1")).thenReturn(buildDTO());

        mockMvc.perform(post("/api/missions/m-1/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", is("Mission cancelled")));
    }

    @Test
    @DisplayName("POST /api/missions/{id}/nodes/{nodeId}/retry 应重试节点")
    void retryNode_retries() throws Exception {
        when(missionService.retryNode("m-1", "n1")).thenReturn(buildDTO());

        mockMvc.perform(post("/api/missions/m-1/nodes/n1/retry"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", is("Node retry triggered")));
    }

    @Test
    @DisplayName("POST /api/missions/{id}/nodes/{nodeId}/skip 应跳过节点")
    void skipNode_skips() throws Exception {
        when(missionService.skipNode("m-1", "n2")).thenReturn(buildDTO());

        mockMvc.perform(post("/api/missions/m-1/nodes/n2/skip"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", is("Node skipped")));
    }

    @Test
    @DisplayName("GET /api/missions/stats 应返回统计信息")
    void getStats_returns() throws Exception {
        when(missionService.getStats()).thenReturn(Map.of("totalMissions", 5));

        mockMvc.perform(get("/api/missions/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalMissions", is(5)));
    }
}
