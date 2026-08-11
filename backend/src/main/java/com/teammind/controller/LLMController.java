package com.teammind.controller;

import com.teammind.dto.ApiResponse;
import com.teammind.llm.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * LLM Controller
 * 
 * LLM 相关 API
 */
@RestController
@RequestMapping("/api/llm")
@RequiredArgsConstructor
public class LLMController {

    private final LLMService llmService;

    /**
     * 获取 LLM 状态
     */
    @GetMapping("/status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getStatus() {
        Map<String, Object> status = llmService.getStatus();
        return ResponseEntity.ok(ApiResponse.success(status));
    }

    /**
     * 获取可用提供商
     */
    @GetMapping("/providers")
    public ResponseEntity<ApiResponse<List<String>>> getAvailableProviders() {
        List<String> providers = llmService.getAvailableProviders();
        return ResponseEntity.ok(ApiResponse.success(providers));
    }

    /**
     * 测试 LLM 连接
     */
    @PostMapping("/test")
    public ResponseEntity<ApiResponse<LLMResponse>> testConnection(
            @RequestBody(required = false) Map<String, String> body) {
        
        String testPrompt = body != null ? body.get("prompt") : null;
        if (testPrompt == null || testPrompt.isEmpty()) {
            testPrompt = "Hello! Please respond with 'LLM connection successful!' to confirm you're working.";
        }
        
        LLMResponse response = llmService.chat(testPrompt);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 发送聊天请求
     */
    @PostMapping("/chat")
    public ResponseEntity<ApiResponse<LLMResponse>> chat(@RequestBody ChatRequest request) {
        LLMRequest.LLMRequestBuilder builder = LLMRequest.builder()
                .messages(request.getMessages());
        
        if (request.getModel() != null) {
            builder.model(request.getModel());
        }
        if (request.getTemperature() != null) {
            builder.temperature(request.getTemperature());
        }
        if (request.getMaxTokens() != null) {
            builder.maxTokens(request.getMaxTokens());
        }
        
        LLMResponse response = llmService.chat(builder.build());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 使用指定提供商发送请求
     */
    @PostMapping("/chat/{provider}")
    public ResponseEntity<ApiResponse<LLMResponse>> chatWithProvider(
            @PathVariable String provider,
            @RequestBody ChatRequest request) {
        
        LLMRequest.LLMRequestBuilder builder = LLMRequest.builder()
                .messages(request.getMessages());
        
        if (request.getModel() != null) {
            builder.model(request.getModel());
        }
        
        LLMResponse response = llmService.chat(provider, builder.build());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 聊天请求 DTO
     */
    @lombok.Data
    public static class ChatRequest {
        private List<LLMRequest.Message> messages;
        private String model;
        private Double temperature;
        private Integer maxTokens;
    }
}
