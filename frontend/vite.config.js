import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [
    vue(),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    },
  },
  // bpmn-js 依赖预构建，解决导入找不到包报错
  optimizeDeps: {
    include: [
      'bpmn-js',
      'bpmn-js-properties-panel'
    ]
  },
  server: {
    proxy: {
      '/api': {
        target:'http://localhost:8080',
        changeOrigin: true,
        secure: false,
        rewrite: (path) => path.replace(/^\/api/, ''),
      },
      '/deploy-api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        secure: false,
      },
      '/agent': {
        target: 'http://127.0.0.1:8000',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/agent/, ''),
        // SSE 流需要即时透传，禁用任何缓冲/压缩
        ws: true,
        configure: (proxy, _options) => {
          proxy.on('error', (err, _req, _res) => {
            console.error('agent proxy error:', err.message)
          })
          proxy.on('proxyRes', (proxyRes, req, _res) => {
            proxyRes.headers['cache-control'] = 'no-cache'
            proxyRes.headers['x-accel-buffering'] = 'no'
            if (req.url?.startsWith('/api/agent/chat')) {
              delete proxyRes.headers['content-length']
            }
          })
        },
      }
    }
  }
})