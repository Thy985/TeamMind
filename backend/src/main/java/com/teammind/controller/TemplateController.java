package com.teammind.controller;

import com.teammind.dto.*;
import com.teammind.service.TemplateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Template Controller
 * 
 * 团队模板管理 API
 */
@RestController
@RequestMapping("/api/templates")
@RequiredArgsConstructor
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:5173"})
public class TemplateController {

    private final TemplateService templateService;

    /**
     * 获取所有模板
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<TemplateDTO>>> listTemplates() {
        List<TemplateDTO> templates = templateService.listTemplates();
        return ResponseEntity.ok(ApiResponse.success(templates));
    }

    /**
     * 获取公开模板
     */
    @GetMapping("/public")
    public ResponseEntity<ApiResponse<List<TemplateDTO>>> listPublicTemplates() {
        List<TemplateDTO> templates = templateService.listPublicTemplates();
        return ResponseEntity.ok(ApiResponse.success(templates));
    }

    /**
     * 获取我的模板
     */
    @GetMapping("/mine")
    public ResponseEntity<ApiResponse<List<TemplateDTO>>> listMyTemplates() {
        List<TemplateDTO> templates = templateService.listMyTemplates();
        return ResponseEntity.ok(ApiResponse.success(templates));
    }

    /**
     * 按分类获取模板
     */
    @GetMapping("/by-category")
    public ResponseEntity<ApiResponse<Map<String, List<TemplateDTO>>>> listTemplatesByCategory() {
        Map<String, List<TemplateDTO>> templates = templateService.listTemplatesByCategory();
        return ResponseEntity.ok(ApiResponse.success(templates));
    }

    /**
     * 获取模板详情
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TemplateDTO>> getTemplate(@PathVariable String id) {
        TemplateDTO template = templateService.getTemplate(id);
        return ResponseEntity.ok(ApiResponse.success(template));
    }

    /**
     * 创建模板
     */
    @PostMapping
    public ResponseEntity<ApiResponse<TemplateDTO>> createTemplate(
            @Valid @RequestBody CreateTemplateRequest request) {
        TemplateDTO template = templateService.createTemplate(request);
        return ResponseEntity.ok(ApiResponse.success(template, "Template created successfully"));
    }

    /**
     * 更新模板
     */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<TemplateDTO>> updateTemplate(
            @PathVariable String id,
            @RequestBody UpdateTemplateRequest request) {
        TemplateDTO template = templateService.updateTemplate(id, request);
        return ResponseEntity.ok(ApiResponse.success(template, "Template updated successfully"));
    }

    /**
     * 删除模板
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteTemplate(@PathVariable String id) {
        templateService.deleteTemplate(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Template deleted successfully"));
    }

    /**
     * 使用模板
     */
    @PostMapping("/{id}/use")
    public ResponseEntity<ApiResponse<TemplateDTO>> useTemplate(@PathVariable String id) {
        TemplateDTO template = templateService.useTemplate(id);
        return ResponseEntity.ok(ApiResponse.success(template));
    }
}
