<script setup lang="ts" generic="T">
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'

interface Props<T> {
  items: T[]
  itemSize: number
  buffer?: number
  renderKey?: (item: T, index: number) => string | number
}

const props = withDefaults(defineProps<Props<T>>(), {
  buffer: 5
})

const emit = defineEmits<{
  (e: 'scroll', event: Event): void
}>()

const containerRef = ref<HTMLDivElement>()
const scrollTop = ref(0)
const containerHeight = ref(0)

// 计算可见范围
const visibleRange = computed(() => {
  const startIndex = Math.max(0, Math.floor(scrollTop.value / props.itemSize) - props.buffer)
  const endIndex = Math.min(
    props.items.length,
    Math.ceil((scrollTop.value + containerHeight.value) / props.itemSize) + props.buffer
  )
  return { startIndex, endIndex }
})

// 可见项
const visibleItems = computed(() => {
  const { startIndex, endIndex } = visibleRange.value
  return props.items.slice(startIndex, endIndex).map((item, index) => ({
    item,
    index: startIndex + index,
    offset: (startIndex + index) * props.itemSize
  }))
})

// 总高度
const totalHeight = computed(() => props.items.length * props.itemSize)

// 处理滚动
function handleScroll(event: Event) {
  const target = event.target as HTMLDivElement
  scrollTop.value = target.scrollTop
  emit('scroll', event)
}

// 初始化
onMounted(() => {
  if (containerRef.value) {
    containerHeight.value = containerRef.value.clientHeight
    const resizeObserver = new ResizeObserver(() => {
      if (containerRef.value) {
        containerHeight.value = containerRef.value.clientHeight
      }
    })
    resizeObserver.observe(containerRef.value)

    onBeforeUnmount(() => {
      resizeObserver.disconnect()
    })
  }
})
</script>

<template>
  <div
    ref="containerRef"
    class="virtual-list"
    @scroll="handleScroll"
  >
    <div class="virtual-list-spacer" :style="{ height: totalHeight + 'px' }">
      <div
        v-for="{ item, index, offset } in visibleItems"
        :key="props.renderKey?.(item, index) ?? index"
        class="virtual-list-item"
        :style="{ transform: `translateY(${offset}px)` }"
      >
        <slot :item="item" :index="index" />
      </div>
    </div>
  </div>
</template>

<style scoped>
.virtual-list {
  width: 100%;
  height: 100%;
  overflow-y: auto;
  overflow-x: hidden;
  position: relative;
}

.virtual-list-spacer {
  position: relative;
  width: 100%;
}

.virtual-list-item {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  width: 100%;
}
</style>
