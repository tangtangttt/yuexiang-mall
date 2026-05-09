import request from '@/utils/request'
import { getToken } from '@/utils/auth'

/**
 * 流式对话 URL（GET + fetch，需自行带 Authorization）
 * 与 axios 一致使用 VUE_APP_BASE_API，开发环境经 devServer 代理到后端
 */
export function buildChatStreamUrl(sessionId, question) {
  const base = process.env.VUE_APP_BASE_API || '/dev-api'
  const params = new URLSearchParams()
  if (sessionId) {
    params.set('sessionId', sessionId)
  }
  params.set('question', question)
  return `${base}/customer/chat/stream?${params.toString()}`
}

export function stopCustomerStream(sessionId) {
  return request({
    url: '/customer/stop',
    method: 'get',
    params: { sessionId }
  })
}

export function getCustomerAuthHeader() {
  const t = getToken()
  return t ? { Authorization: 'Bearer ' + t } : {}
}
