import Vue from 'vue'
import VueRouter from 'vue-router'
import { getToken } from '@/utils/auth'
import store from '@/store'
import { Toast } from 'vant'

Vue.use(VueRouter)

const routes = [
  // AI 智能客服（须放在通配符 * 之前）
  {
    path: '/customer/service',
    name: 'CustomerService',
    component: () => import('@/views/customerService'),
    meta: {
      title: 'AI 智能客服',
      requiresAuth: true,
      showTab: false
    }
  },
  // 首页（通配符路由，放在最后）
  {
    path: '*',
    name: 'Home',
    component: () => import(/* webpackPreload: true */ '@/views/home'),
    meta: {
      title: '首页',
      showTab: true,
      keepAlive: true
    }
  },
  // 登录
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/auth/login'),
    meta: {
      title: '登录',
      noAuth: true // 标记不需要登录
    }
  },
  // 注册
  {
    path: '/registry',
    name: 'Registry',
    component: () => import('@/views/auth/login'),
    meta: {
      title: '注册',
      noAuth: true // 标记不需要登录
    }
  },
  // 分类
  {
    path: '/category',
    name: 'Category',
    component: () => import('@/views/category'),
    meta: {
      title: '分类',
      showTab: true,
      keepAlive: true,
      noAuth: true // 分类页不需要登录
    }
  },
  // 购物车
  {
    path: '/cart',
    name: 'Cart',
    component: () => import('@/views/cart'),
    meta: {
      title: '购物车',
      showTab: true,
      requiresAuth: true // 需要登录
    }
  },
  // 我的
  {
    path: '/user',
    name: 'User',
    component: () => import('@/views/user'),
    meta: {
      title: '我的',
      showTab: true,
      requiresAuth: true // 需要登录
    }
  },
  // 用户设置
  {
    path: '/userSetting',
    name: 'UserSetting',
    component: () => import('@/views/userSetting'),
    meta: {
      title: '用户设置',
      showTab: true,
      requiresAuth: true
    }
  },
  // 用户设置-昵称
  {
    path: '/userSetting/nickname',
    name: 'UserSetNickname',
    component: () => import('@/views/userSetting/nickname'),
    meta: {
      title: '设置昵称',
      showTab: true,
      requiresAuth: true
    }
  },
  // 用户设置-生日
  {
    path: '/userSetting/birthday',
    name: 'UserSetBirthday',
    component: () => import('@/views/userSetting/birthday'),
    meta: {
      title: '设置生日',
      showTab: true,
      requiresAuth: true
    }
  },
  // 用户设置-性别
  {
    path: '/userSetting/gender',
    name: 'UserSetGender',
    component: () => import('@/views/userSetting/gender'),
    meta: {
      title: '设置性别',
      showTab: true,
      requiresAuth: true
    }
  },
  // 用户设置-邮箱
  {
    path: '/userSetting/email',
    name: 'UserSetEmail',
    component: () => import('@/views/userSetting/email'),
    meta: {
      title: '设置邮箱',
      showTab: true,
      requiresAuth: true
    }
  },
  // 用户设置-手机
  {
    path: '/userSetting/mobile',
    name: 'UserSetMobile',
    component: () => import('@/views/userSetting/mobile'),
    meta: {
      title: '设置手机号',
      showTab: true,
      requiresAuth: true
    }
  },
  // 用户设置-密码
  {
    path: '/userSetting/password',
    name: 'UserSetPassword',
    component: () => import('@/views/userSetting/password'),
    meta: {
      title: '设置密码',
      showTab: true,
      requiresAuth: true
    }
  },
  // 订单列表
  {
    path: '/user/order/list/:active',
    name: 'OrderList',
    props: true,
    component: () => import('@/views/order/list'),
    meta: {
      title: '订单列表',
      showTab: true,
      requiresAuth: true
    }
  },
  // 商品列表
  {
    path: '/product/:categoryLevel/:cateId',
    name: 'Product',
    props: true,
    component: () => import('@/views/product'),
    meta: {
      title: '商品列表',
      keepAlive: true,
      noAuth: true // 商品列表不需要登录
    }
  },
  // 金刚位跳转商品列表
  {
    path: '/diamondGoodsList/:diamondId',
    name: 'Diamand',
    props: true,
    component: () => import('@/views/diamondGoodsList'),
    meta: {
      title: '商品列表',
      keepAlive: true,
      noAuth: true
    }
  },
  // 商品详情
  {
    path: '/detail/:goodsId',
    name: 'Detail',
    props: true,
    component: () => import('@/views/detail'),
    meta: {
      title: '商品详情',
      keepAlive: true,
      noAuth: true // 商品详情不需要登录，但加入购物车等操作需要
    }
  },
  // 商品评论
  {
    path: '/detail/comment/:goodsId/:tagType',
    name: 'Comment',
    props: true,
    component: () => import('@/views/detail/comment'),
    meta: {
      title: '商品评论',
      noAuth: true
    }
  },
  // 地址管理
  {
    path: '/address',
    name: 'Address',
    component: () => import('@/views/address/list'),
    meta: {
      title: '地址管理',
      requiresAuth: true
    }
  },
  // 地址编辑
  {
    path: '/address/edit',
    name: 'AddressEdit',
    component: () => import('@/views/address/edit'),
    meta: {
      title: '地址编辑',
      requiresAuth: true
    }
  },
  // 搜索
  {
    path: '/search',
    name: 'Search',
    component: () => import('@/views/search'),
    meta: {
      title: '搜索',
      noAuth: true
    }
  },
  // 搜索结果
  {
    path: '/search/list',
    name: 'SearchList',
    component: () => import('@/views/search/list'),
    meta: {
      title: '搜索结果',
      noAuth: true
    }
  },
  // 确认订单
  {
    path: '/order/confirm',
    name: 'OrderConfirm',
    component: () => import('@/views/order/confirm'),
    meta: {
      title: '确认订单',
      keepAlive: true,
      requiresAuth: true
    }
  },
  // 订单支付
  {
    path: '/order/pay',
    name: 'OrderPay',
    component: () => import('@/views/order/pay'),
    meta: {
      title: '订单支付',
      requiresAuth: true
    }
  },
  // 订单详情
  {
    path: '/order/detail/:orderSn',
    name: 'OrderDetail',
    props: true,
    component: () => import('@/views/order/detail'),
    meta: {
      title: '订单详情',
      requiresAuth: true
    }
  },
  // 支付状态
  {
    path: '/order/payStatus',
    name: 'PayStatus',
    props: true,
    component: () => import('@/views/order/payStatus'),
    meta: {
      requiresAuth: true
    }
  },
  // 优惠券
  {
    path: '/order/coupon',
    name: 'coupon',
    component: () => import('@/views/order/coupon'),
    meta: {
      title: '优惠券',
      showTab: true,
      requiresAuth: true
    }
  },
  // 优惠券
  {
    path: '/order/my',
    name: 'couponMy',
    component: () => import('@/views/order/my'),
    meta: {
      title: '优惠券',
      showTab: true,
      requiresAuth: true
    }
  },
  // 评论页面
  {
    path: '/goodsComment/:orderGoodsId/:goodsId',
    name: 'GoodsComment',
    props: true,
    component: () => import('@/views/goodsComment'),
    meta: {
      title: '商品评论',
      requiresAuth: true
    }
  }
]

const router = new VueRouter({
  // 记录上个页面的滚动位置
  scrollBehavior(to, from, savedPosition) {
    if (savedPosition) {
      return savedPosition
    } else {
      return { x: 0, y: 0 }
    }
  },
  routes
})

// ========== 路由守卫 ==========
// 判断是否需要登录的函数
function requiresAuth(route) {
  // 如果有 requiresAuth: true 则需要登录
  if (route.meta && route.meta.requiresAuth === true) {
    return true
  }
  // 如果有 noAuth: true 则不需要登录
  if (route.meta && route.meta.noAuth === true) {
    return false
  }
  // 默认：没有明确标记的页面，需要登录（因为购物车、个人中心等需要）
  // 但首页、分类、商品详情等应该不需要，所以建议显式标记
  return false
}

router.beforeEach(async(to, from, next) => {
  // 设置页面标题
  if (to.meta && to.meta.title) {
    document.title = to.meta.title
  }

  const hasToken = getToken()
  const needAuth = requiresAuth(to)

  if (needAuth) {
    // 需要登录的页面
    if (!hasToken) {
      // 没有 token，跳转到登录页
      Toast.fail('请先登录')
      next({
        path: '/login',
        query: { redirect: to.fullPath } // 保存登录后要跳转的页面
      })
      return
    }

    // 有 token，检查用户信息是否已加载
    try {
      const hasUserInfo = store.state.user.userInfo && Object.keys(store.state.user.userInfo).length > 0

      if (!hasUserInfo) {
        await store.dispatch('user/getInfo')
      }
      next()
    } catch (error) {
      // token 无效或获取用户信息失败

      await store.dispatch('user/resetToken')
      Toast.fail('登录已过期，请重新登录')
      next({
        path: '/login',
        query: { redirect: to.fullPath }
      })
    }
  } else {
    // 不需要登录的页面
    if (to.path === '/login' && hasToken) {
      // 已登录用户访问登录页，重定向到首页

      next({ path: '/' })
    } else {
      next()
    }
  }
})

export default router
