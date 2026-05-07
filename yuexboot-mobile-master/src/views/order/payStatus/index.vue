<template>
  <div class="order-status">
    <Header :status="status" />
  </div>
</template>

<script>
import Header from './modules/Header'
import { alipaySyncPaid } from '@/api/pay'

export default {
  name: 'OrderStatus',
  components: {
    Header
  },
  props: {
    status: {
      type: String,
      default: ''
    }
  },
  mounted() {
    const outTradeNo = this.$route.query.out_trade_no
    if (outTradeNo) {
      alipaySyncPaid(outTradeNo).catch(() => {})
    }
  }
}
</script>

<style lang="scss" scoped>
.order-status {
  min-height: 100vh;
  background: #f5f5f5;
}
</style>
