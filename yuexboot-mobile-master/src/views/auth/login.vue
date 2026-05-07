<template>
  <div class="login-container">
    <van-form class="form" @submit="onSubmit">
      <div class="title-big">yuexboot-mall官网商城</div>
      <div class="title">若该手机号未注册，我们将自动为你注册</div>
      <van-field
        v-model="form.mobile"
        type="text"
        clearable
        name=""
        label=""
        placeholder="请输入手机号码"
        :rules="[
          {
            validator: checkPhone,
          },
        ]"
      />

      <van-field
        v-model="form.yzm"
        center
        clearable
        label=""
        placeholder="请输入短信验证码"
      >
        <template #button>
          <span @click.stop="getMobileCode">{{ btnText }}</span>
        </template>
      </van-field>

      <div class="submitDiv">
        <van-button
          round
          block
          :loading="loading"
          type="info"
          loading-text="登录中..."
          native-type="submit"
          class="submitBtn"
        >登录</van-button>
      </div>
    </van-form>
    <van-checkbox v-model="checked" checked-color="crimson">已阅读并同意《用户协议》《隐私协议》</van-checkbox>
  </div>
</template>

<script>
import { getMobileCode } from '@/api/login'

// ========== 调试开关 ==========
const ENABLE_DEBUG = true
const debugLog = (...args) => {
  if (ENABLE_DEBUG) {
    console.log('[Login DEBUG]', ...args)
  }
}
// =============================

export default {
  name: 'Login',
  data() {
    return {
      form: {
        mobile: '',
        yzm: ''
      },
      loading: false,
      disabled: false,
      totalCount: 0,
      checked: false,
      isKeyboardOpen: false,
      redirect: '',
      interval: null  // 添加定时器变量
    }
  },
  computed: {
    btnText() {
      return this.totalCount !== 0 ? `${this.totalCount}秒后获取` : '获取验证码'
    }
  },
  watch: {
    $route: {
      handler(route) {
        this.redirect = (route.query && route.query.redirect) || '/'
        debugLog('路由变化，redirect:', this.redirect)
      },
      immediate: true
    }
  },
  mounted() {
    debugLog('Login 组件挂载')
    window.addEventListener('resize', this.checkKeyboard)
    this.checkKeyboard()

    const existingToken = this.$store.getters.token
    debugLog('mounted 时 store.getters.token:', existingToken)
    if (existingToken) {
      debugLog('检测到已存在的 token，可能会自动跳转')
    }
  },
  beforeDestroy() {
    window.removeEventListener('resize', this.checkKeyboard)
    // 清理验证码倒计时定时器
    if (this.interval) {
      clearInterval(this.interval)
    }
  },
  methods: {
    getMobileCode() {
      debugLog('getMobileCode 被调用，手机号:', this.form.mobile)

      if (this.disabled) {
        debugLog('获取验证码按钮被禁用，跳过')
        return
      }

      const { mobile } = this.form
      if (!mobile || !this.checkPhone(mobile)) {
        debugLog('手机号验证失败:', mobile)
        this.$toast.fail('请先输入正确的手机号码')
        return
      }

      debugLog('开始获取验证码，手机号:', mobile)
      this.disabled = true
      this.totalCount = 60

      // 清除已有定时器
      if (this.interval) {
        clearInterval(this.interval)
      }

      this.interval = setInterval(() => {
        this.totalCount--
        if (this.totalCount <= 0) {
          clearInterval(this.interval)
          this.disabled = false
          this.totalCount = 0
          debugLog('验证码倒计时结束')
        }
      }, 1000)

      getMobileCode({ mobile }).then((res) => {
        debugLog('获取验证码响应:', res)
        this.form.emailKey = res.data.key
        debugLog('保存的 emailKey:', this.form.emailKey)
        this.$notify({
          type: 'success',
          message: '短信验证码已发送',
          duration: 2000
        })
      }).catch(err => {
        debugLog('获取验证码失败:', err)
        // 请求失败时重置倒计时
        if (this.interval) {
          clearInterval(this.interval)
        }
        this.disabled = false
        this.totalCount = 0
      })
    },

    checkPhone(num) {
      if (num === 123456789) {
        debugLog('使用测试手机号: 123456789')
        return true
      }
      const reg = /^[1][3,4,5,7,8,9][0-9]{9}$/
      const isValid = reg.test(num)
      debugLog(`手机号 ${num} 验证结果: ${isValid}`)
      return isValid
    },

    async onSubmit() {
      debugLog('===== 登录提交开始 =====')
      debugLog('表单数据:', { mobile: this.form.mobile, yzm: this.form.yzm })

      if (!this.checked) {
        debugLog('用户协议未勾选')
        this.$toast.fail('请先勾选同意用户协议及隐私政策')
        return
      }

      if (!this.form.yzm) {
        debugLog('验证码为空')
        this.$toast.fail('请输入验证码')
        return
      }

      debugLog('开始登录流程...')
      this.loading = true

      try {
        // 步骤1: 调用 login action（只存储 token，不跳转）
        debugLog('步骤1: 调用 user/login')
        await this.$store.dispatch('user/login', {
          mobile: this.form.mobile,
          yzm: this.form.yzm
        })
        debugLog('login 完成')

        // 检查登录后 token 是否已存储
        const tokenAfterLogin = this.$store.getters.token
        debugLog('login 后 store.getters.token:', tokenAfterLogin ? `${tokenAfterLogin.substring(0, 30)}...` : 'null')

        if (!tokenAfterLogin) {
          debugLog('警告: login 后 token 为空！')
          throw new Error('登录失败，未获取到 token')
        }

        // 步骤2: 获取用户信息
        debugLog('步骤2: 调用 user/getInfo')
        const userInfo = await this.$store.dispatch('user/getInfo')
        debugLog('getInfo 完成，用户信息:', userInfo)

        // 步骤3: 获取地址列表（可选，失败不影响登录）
        try {
          debugLog('步骤3: 调用 address/getList')
          await this.$store.dispatch('address/getList')
          debugLog('地址列表获取完成')
        } catch (addressError) {
          // 地址列表获取失败不影响登录流程
          debugLog('获取地址列表失败（不影响登录）:', addressError)
        }

        debugLog('登录成功，准备跳转到:', this.redirect)

        // 成功提示
        this.$toast.success('登录成功')

        // 延迟跳转，让用户看到成功提示
        setTimeout(() => {
          this.$router.push(this.redirect)
        }, 500)

      } catch (e) {
        debugLog('登录失败:', e)
        debugLog('错误详情:', {
          message: e.message,
          response: e.response,
          code: e.code
        })
        this.$toast.fail(e.message || '登录失败，请重试')
      } finally {
        this.loading = false
        debugLog('===== 登录流程结束 =====')
      }
    },

    checkKeyboard() {
      this.isKeyboardOpen = window.innerHeight > document.documentElement.clientHeight
      debugLog('键盘状态:', this.isKeyboardOpen ? '打开' : '关闭')
    }
  }
}
</script>

<style lang="scss" scoped>
.login-container {
  .header {
    display: flex;
    flex-direction: column;
    justify-content: center;
    align-items: center;

    .header__logo {
      display: block;
      width: 100%;
    }
    .header__title {
      font-size: 36px;
      color: #000;
      font-weight: normal;
      padding-top: 30px;
    }
  }

  .form {
    margin-top:200px;
    padding: 24px;
    .title-big{
      font-size: 42px;
      color: #000;
      font-weight: normal;
      padding: 0 20px 20px 20px;
    }
    .title {
      color: #b6b6b6;
      padding: 0 20px 40px 20px;
    }
    ::v-deep .van-cell{
      border-bottom: 1px solid #b6b6b6;
      line-height: 40px;
    }
    ::v-deep .van-cell:after{
      display: none;
    }
  }
  ::v-deep .van-checkbox{
    position: absolute;
    bottom: -40vh;
    left: 50%;
    transform: translateX(-50%);
    width: max-content;

    .van-checkbox__label{
      color: #b6b6b6;
    }
  }

  .submitDiv{
    margin:15vw  auto 0;
    width: max-content;
  }
  .submitBtn{
    background-color: crimson;
    border: none;
    width:500px;
    font-size: 30px;
  }
}
</style>
