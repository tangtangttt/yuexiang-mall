<template>
  <div class="user-tools">
    <van-cell title="AI 智能客服" is-link to="/customer/service" class="user-tools__ai">
      <template #icon>
        <span class="user-tools__ai-icon"><van-icon name="chat-o" /></span>
      </template>
    </van-cell>
    <van-cell title="优惠券" is-link to="order/my" />
    <van-cell title="收货地址" is-link to="address" />
    <van-cell title="退出登录" is-link @click="logout" />
  </div>
</template>

<script>
export default {
  methods: {
    logout() {
      this.$store.dispatch('user/logout')
        .then(() => {
          this.$notify({
            type: 'success',
            message: '退出成功',
            duration: 2000,
            onOpened: () => {
              this.$router.push({ name: 'Login' })
            }
          })
        })
    }
  }
}
</script>

<style>
:root {
  --user-tools-shadow: 0px 2px 16px 0px rgba(0, 0, 0, 0.09);
}
</style>

<style lang="scss" scoped>
.user-tools {
  width: 702px;
  background: rgba(255, 255, 255, 1);
  margin: 0 auto;
  margin-top: 24px;
  box-shadow: var(--user-tools-shadow); // 这里使用 css 函数，是为了避免旧版本 vue-cli 依赖的 css 压缩工具（mini-css-extract-plugin）导致的 bug
  border-radius: 24px;
  overflow: hidden;
}

.user-tools__ai-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 64px;
  height: 64px;
  margin-right: 16px;
  border-radius: 16px;
  background: linear-gradient(135deg, #fff0ef 0%, #ffe4e1 100%);
  color: #f1574f;
  font-size: 36px;
}
</style>
