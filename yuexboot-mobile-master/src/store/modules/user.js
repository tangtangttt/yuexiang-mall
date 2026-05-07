import { login, logout, getInfo } from '@/api/user'
import { getToken, setToken, removeToken } from '@/utils/auth'

const state = {
  token: getToken(),
  userInfo: {}
}

const mutations = {
  SET_TOKEN(state, token) {
    state.token = token
  },
  SET_USER_INFO(state, userInfo) {
    state.userInfo = userInfo
  }
}

const actions = {
  // 登录
  async login({ commit }, userInfo) {
    try {
      const { mobile, yzm } = userInfo

      const response = await login({ mobile, yzm })

      // 根据你的 API 响应结构调整 token 获取方式
      let token = null
      if (response.data && response.data.token) {
        token = response.data.token
      } else if (response.token) {
        token = response.token
      } else if (typeof response.data === 'string') {
        token = response.data
      } else {
        throw new Error('API 响应中没有找到 token')
      }

      commit('SET_TOKEN', token)
      setToken(token)

      return token
    } catch (error) {
      throw error
    }
  },

  // 获取用户信息
  async getInfo({ commit, state }) {
    const token = state.token || getToken()
    if (!token) {
      throw new Error('Token 不存在，请重新登录')
    }

    try {
      const response = await getInfo()

      // 根据你的 API 响应结构调整
      let userInfo = null
      if (response.data && response.data.userInfo) {
        userInfo = response.data.userInfo
      } else if (response.userInfo) {
        userInfo = response.userInfo
      } else if (response.data) {
        userInfo = response.data
      } else {
        userInfo = response
      }

      if (!userInfo) {
        throw new Error('获取用户信息失败')
      }

      commit('SET_USER_INFO', userInfo)
      return userInfo
    } catch (error) {

      throw error
    }
  },

  // 登出
  async logout({ commit }) {
    try {
      await logout()
    } catch (error) {

    } finally {
      commit('SET_TOKEN', '')
      commit('SET_USER_INFO', {})
      removeToken()
    }
  },

  // 重置 token
  resetToken({ commit }) {
    commit('SET_TOKEN', '')
    commit('SET_USER_INFO', {})
    removeToken()
  }
}

export default {
  namespaced: true,
  state,
  mutations,
  actions
}
