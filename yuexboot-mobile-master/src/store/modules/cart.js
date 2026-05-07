import { getCartGoodsCount } from '@/api/cart'

const state = {
    cartCount: 0
}

const getters = {
    cartCount: state => state.cartCount
}

const mutations = {
    SET_CART_COUNT(state, count) {
        state.cartCount = count
    }
}

const actions = {
    // 获取购物车数量
    getCartCount({ commit }) {
        return new Promise((resolve, reject) => {
            getCartGoodsCount().then(res => {
                const { data } = res
                commit('SET_CART_COUNT', data)
                resolve(data)
            }).catch(e => {
                reject(e)
            })
        })
    },

    // 加入购物车后刷新数量
    refreshCartCount({ dispatch }) {
        return dispatch('getCartCount')
    }
}

export default {
    namespaced: true,
    state,
    getters,
    mutations,
    actions
}