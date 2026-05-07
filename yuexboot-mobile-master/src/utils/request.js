import axios from 'axios'
import qs from 'qs'
import { Toast, Dialog } from 'vant'
import store from '@/store'
import { getToken } from '@/utils/auth'
import router from '@/router'

// ========== 调试开关：上线时改为 false 即可 ==========
const ENABLE_DEBUG = false  // 改为 false 关闭所有调试日志

// 封装调试函数
const debugLog = (...args) => {
  if (ENABLE_DEBUG) {
    console.log('[DEBUG]', ...args)
  }
}
// =================================================

// 创建一个axios实例
const service = axios.create({
  baseURL: process.env.VUE_APP_BASE_API,
  // withCredentials: true,
  timeout: 5000
})

// 请求拦截器
service.interceptors.request.use(
  config => {
    debugLog('===== 请求拦截器开始 =====')
    debugLog('请求URL:', config.url)
    debugLog('请求方法:', config.method)

    // 打印 store.getters.token 原始值
    debugLog('store.getters.token 值:', store.getters.token)

    if (store.getters.token) {
      const token = getToken()
      debugLog('getToken() 获取到的 token:', token)
      debugLog('token 类型:', typeof token)
      debugLog('token 长度:', token ? token.length : 0)
      debugLog('token 前20字符:', token ? token.substring(0, 20) + '...' : 'null')

      if (token) {
        config.headers.Authorization = 'Bearer ' + token
        debugLog('设置的 Authorization 头:', config.headers.Authorization)
      } else {
        debugLog('警告: getToken() 返回了空值!')
      }
    } else {
      debugLog('store.getters.token 为假值，不添加 Authorization 头')
    }

    debugLog('最终请求头:', JSON.stringify(config.headers, null, 2))
    debugLog('===== 请求拦截器结束 =====')
    return config
  },
  error => {
    console.log(error)
    return Promise.reject(error)
  }
)

// 响应拦截器
service.interceptors.response.use(
  response => {
    debugLog('===== 响应拦截器开始 =====')
    debugLog('响应状态码:', response.status)
    debugLog('响应头:', response.headers)
    debugLog('响应数据:', response.data)

    const res = response.data
    debugLog('返回码 res.code:', res.code)

    // 返回码不正确
    if (res.code !== 200) {
      debugLog(`返回码异常: code=${res.code}, msg=${res.msg}`)

      // 401未登陆
      if (res.code === 401) {
        debugLog('!!! 触发 401 未登录逻辑 !!!')
        debugLog('当前存储的 token 状态:', {
          storeGettersToken: store.getters.token,
          getTokenValue: getToken(),
          hasToken: !!getToken()
        })

        Dialog.alert({
          title: '提示',
          message: '您还未登录，请登录'
        }).then(() => {
          store.dispatch('user/resetToken').then(() => {
            const currentPath = router.currentRoute.path
            debugLog('重置 token 后，跳转前路径:', currentPath)
            if (currentPath !== '/login') {
              router.push({ path: '/login' })
            }
          })
        })
        return Promise.resolve({code: 401, data: null, msg:'未登录'})
      }

      // 订单轮询
      if (res.code === 5001) {
        debugLog('订单轮询 code 5001')
        return Promise.resolve(res)
      }

      if (res.code === 50148) {
        debugLog('特殊 code 50148，直接返回数据')
        return res
      }

      debugLog('显示错误提示:', res.msg)
      Toast.fail(res.msg)
      return Promise.resolve(res)
    } else {
      debugLog('返回码正常 (code=200)')
      debugLog('===== 响应拦截器结束 =====')
      return res
    }
  },
  // 响应拦截器 - error 部分
  error => {
    console.log('err' + error)
    debugLog('===== 响应错误拦截 =====')
    debugLog('错误对象:', error)
    debugLog('错误响应:', error.response)

    if (error.response) {
      const status = error.response.status
      const data = error.response.data

      debugLog('错误状态码:', status)
      debugLog('错误数据:', data)

      // ========== 关键修复：处理 401 ==========
      if (status === 401) {
        debugLog('!!! 收到 HTTP 401，执行未登录处理 !!!')

        // 清除 token
        store.dispatch('user/resetToken').then(() => {
          Dialog.alert({
            title: '提示',
            message: '您还未登录，请登录'
          }).then(() => {
            const currentPath = router.currentRoute.path
            if (currentPath !== '/login') {
              router.push({ path: '/login' })
            }
          })
        })

        // 返回 resolved 对象，避免 Uncaught in promise
        return Promise.resolve({ code: 401, data: null, msg: '未登录' })
      }
      // =======================================

      // 其他 HTTP 错误
      const msg = data.message || data.msg || data.error || `请求失败(${status})`
      Toast.fail(msg)
      return Promise.resolve({ code: status, data: null, msg })
    } else {
      // 网络错误
      Toast.fail('网络连接异常')
      return Promise.resolve({ code: 0, data: null, msg: '网络连接异常' })
    }
  }
)

/**
 * 使用 application/x-www-form-urlencoded format
 * @param {*} url
 * @param {*} postData
 * @returns
 */
service.formDataPost = function (url, postData) {
  const options = {
    method: 'POST',
    headers: { 'content-type': 'application/x-www-form-urlencoded' },
    data: qs.stringify(postData),
    url
  }
  debugLog('formDataPost 请求:', { url, postData, stringifiedData: qs.stringify(postData) })
  return service(options)
}

export default service
