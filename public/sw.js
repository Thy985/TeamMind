/**
 * Service Worker - 离线缓存和性能优化
 */

const CACHE_NAME = 'teammind-v1.0.0'
const STATIC_ASSETS = [
  '/',
  '/index.html',
  '/manifest.json'
]

// 安装事件 - 缓存静态资源
self.addEventListener('install', (event) => {
  console.log('[SW] Installing...')
  
  event.waitUntil(
    caches.open(CACHE_NAME).then((cache) => {
      console.log('[SW] Caching static assets')
      return cache.addAll(STATIC_ASSETS)
    })
  )
  
  // 立即激活
  self.skipWaiting()
})

// 激活事件 - 清理旧缓存
self.addEventListener('activate', (event) => {
  console.log('[SW] Activating...')
  
  event.waitUntil(
    caches.keys().then((cacheNames) => {
      return Promise.all(
        cacheNames
          .filter((name) => name !== CACHE_NAME)
          .map((name) => {
            console.log('[SW] Deleting old cache:', name)
            return caches.delete(name)
          })
      )
    })
  )
  
  // 立即控制所有页面
  self.clients.claim()
})

// 请求拦截 - 缓存优先策略
self.addEventListener('fetch', (event) => {
  const { request } = event
  const url = new URL(request.url)

  // 只处理同源请求
  if (url.origin !== location.origin) {
    return
  }

  // API 请求 - 网络优先
  if (url.pathname.startsWith('/api')) {
    event.respondWith(networkFirst(request))
    return
  }

  // 静态资源 - 缓存优先
  if (
    request.destination === 'style' ||
    request.destination === 'script' ||
    request.destination === 'image' ||
    request.destination === 'font'
  ) {
    event.respondWith(cacheFirst(request))
    return
  }

  // HTML - 网络优先
  if (request.destination === 'document') {
    event.respondWith(networkFirst(request))
    return
  }

  // 默认 - 缓存优先
  event.respondWith(cacheFirst(request))
})

/**
 * 缓存优先策略
 */
async function cacheFirst(request) {
  const cachedResponse = await caches.match(request)
  
  if (cachedResponse) {
    // 返回缓存，同时更新缓存
    fetchAndCache(request)
    return cachedResponse
  }
  
  // 缓存未命中，获取网络资源并缓存
  return fetchAndCache(request)
}

/**
 * 网络优先策略
 */
async function networkFirst(request) {
  try {
    const networkResponse = await fetch(request)
    
    // 如果请求成功，缓存响应
    if (networkResponse.ok) {
      const cache = await caches.open(CACHE_NAME)
      cache.put(request, networkResponse.clone())
    }
    
    return networkResponse
  } catch (error) {
    // 网络失败，返回缓存
    const cachedResponse = await caches.match(request)
    
    if (cachedResponse) {
      return cachedResponse
    }
    
    // 缓存也没有，返回离线页面
    return caches.match('/')
  }
}

/**
 * 获取并缓存
 */
async function fetchAndCache(request) {
  try {
    const networkResponse = await fetch(request)
    
    if (networkResponse.ok) {
      const cache = await caches.open(CACHE_NAME)
      cache.put(request, networkResponse.clone())
    }
    
    return networkResponse
  } catch (error) {
    console.error('[SW] Fetch failed:', error)
    throw error
  }
}

// 后台同步
self.addEventListener('sync', (event) => {
  console.log('[SW] Background sync:', event.tag)
  
  if (event.tag === 'sync-data') {
    event.waitUntil(syncData())
  }
})

/**
 * 同步数据
 */
async function syncData() {
  // 获取待同步的数据
  const pendingData = await getPendingData()
  
  for (const data of pendingData) {
    try {
      await fetch(data.url, {
        method: data.method,
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(data.body)
      })
      
      // 删除已同步的数据
      await removePendingData(data.id)
    } catch (error) {
      console.error('[SW] Sync failed:', error)
    }
  }
}

/**
 * 获取待同步数据
 */
async function getPendingData() {
  // 这里应该从 IndexedDB 获取
  return []
}

/**
 * 删除待同步数据
 */
async function removePendingData(id) {
  // 从 IndexedDB 删除
}

// 推送通知
self.addEventListener('push', (event) => {
  console.log('[SW] Push received')
  
  if (!event.data) {
    return
  }
  
  const data = event.data.json()
  
  const options = {
    body: data.body || '您有一条新消息',
    icon: '/icons/icon-192x192.png',
    badge: '/icons/badge-72x72.png',
    vibrate: [100, 50, 100],
    data: {
      url: data.url || '/'
    },
    actions: [
      { action: 'open', title: '打开' },
      { action: 'close', title: '关闭' }
    ]
  }
  
  event.waitUntil(
    self.registration.showNotification(data.title || 'TeamMind', options)
  )
})

// 通知点击
self.addEventListener('notificationclick', (event) => {
  console.log('[SW] Notification click')
  
  event.notification.close()
  
  if (event.action === 'close') {
    return
  }
  
  const url = event.notification.data?.url || '/'
  
  event.waitUntil(
    clients.matchAll({ type: 'window' }).then((clientList) => {
      // 如果已有窗口，打开该窗口
      for (const client of clientList) {
        if (client.url === url && 'focus' in client) {
          return client.focus()
        }
      }
      
      // 如果没有，打开新窗口
      if (clients.openWindow) {
        return clients.openWindow(url)
      }
    })
  )
})

console.log('[SW] Service Worker loaded')
