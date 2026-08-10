<script setup lang="ts">
import { computed } from 'vue'
import { NCard, NButton, NIcon, NTag } from 'naive-ui'
import { SparklesOutline, TimeOutline, TrendingUpOutline, AlertCircleOutline } from '@vicons/ionicons5'
import { useAIRecommendations } from '@/composables/useAIRecommendations'

const { recommendations, isLoading, fetchRecommendations } = useAIRecommendations()

const recommendationIcons = {
  mission: '🎯',
  agent: '🤖',
  template: '📋',
  optimization: '⚡'
}

const recommendationColors = {
  mission: '#6366f1',
  agent: '#10b981',
  template: '#f59e0b',
  optimization: '#3b82f6'
}

function handleAction(action: any) {
  if (action?.onClick) {
    action.onClick()
  }
}

// 获取推荐卡片样式
function getCardStyle(type: string) {
  return {
    borderLeft: `4px solid ${recommendationColors[type as keyof typeof recommendationColors] || '#6366f1'}`
  }
}

// 初始化加载
fetchRecommendations()
</script>

<template>
  <div class="ai-recommendations">
    <div class="recommendations-header">
      <h3>
        <NIcon><SparklesOutline /></NIcon>
        AI 智能推荐
      </h3>
      <NButton text @click="fetchRecommendations" :loading="isLoading">
        刷新
      </NButton>
    </div>

    <div v-if="isLoading" class="loading">
      正在分析您的数据...
    </div>

    <div v-else-if="recommendations.length === 0" class="empty">
      暂无推荐
    </div>

    <div v-else class="recommendations-list">
      <NCard
        v-for="rec in recommendations"
        :key="rec.id"
        class="recommendation-card"
        :style="getCardStyle(rec.type)"
        size="small"
      >
        <div class="card-header">
          <span class="card-icon">{{ recommendationIcons[rec.type] }}</span>
          <div class="card-info">
            <h4>{{ rec.title }}</h4>
            <p class="card-description">{{ rec.description }}</p>
          </div>
          <div class="card-score">
            <NTag :type="rec.score >= 80 ? 'success' : rec.score >= 60 ? 'warning' : 'error'" size="small">
              {{ rec.score }}
            </NTag>
          </div>
        </div>

        <div class="card-reason">
          <NIcon><TimeOutline /></NIcon>
          {{ rec.reason }}
        </div>

        <div v-if="rec.actions && rec.actions.length > 0" class="card-actions">
          <NButton
            v-for="(action, index) in rec.actions"
            :key="index"
            size="small"
            @click="handleAction(action)"
          >
            {{ action.label }}
          </NButton>
        </div>
      </NCard>
    </div>

    <div class="ai-tips">
      <NIcon><TrendingUpOutline /></NIcon>
      <span>AI 会根据您的使用习惯持续学习和优化推荐</span>
    </div>
  </div>
</template>

<style scoped>
.ai-recommendations {
  padding: 16px;
}

.recommendations-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.recommendations-header h3 {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0;
  font-size: 16px;
  font-weight: 600;
}

.loading,
.empty {
  text-align: center;
  padding: 32px;
  color: #6b7280;
}

.recommendations-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.recommendation-card {
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.recommendation-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

.card-header {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 12px;
}

.card-icon {
  font-size: 24px;
}

.card-info {
  flex: 1;
}

.card-info h4 {
  margin: 0 0 4px 0;
  font-size: 14px;
  font-weight: 600;
}

.card-description {
  margin: 0;
  font-size: 13px;
  color: #6b7280;
}

.card-score {
  flex-shrink: 0;
}

.card-reason {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: #6b7280;
  margin-bottom: 12px;
}

.card-actions {
  display: flex;
  gap: 8px;
}

.ai-tips {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 16px;
  padding: 12px;
  background: rgba(99, 102, 241, 0.1);
  border-radius: 8px;
  font-size: 13px;
  color: #6366f1;
}
</style>
