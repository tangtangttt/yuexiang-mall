<template>
  <div class="payment">
    <nav-bar :title="$route.meta.title" />
    <!-- closeable 模式，在右侧显示关闭按钮 -->
    <van-notice-bar
      mode="closeable"
    >请在半小时内完成付款，否则系统自动取消订单</van-notice-bar>

    <van-cell-group class="payment_group">
      <van-cell title="订单编号" :value="orderSn" />
      <van-cell title="实付金额">
        <span class="red">{{ actualPrice | yuan }}</span>
      </van-cell>
    </van-cell-group>

    <div class="pay_way_group">
      <div class="pay_way_title">选择支付方式</div>
      <van-radio-group v-model="payWay">
        <van-cell-group>
          <van-cell>
            <template slot="title">
              <p style="font-size: 17px">测试支付方式</p>
            </template>
            <van-radio name="test" />
          </van-cell>
          <van-cell>
            <template slot="title">
              <img
                src="../../../assets/images/ali_pay.png"
                alt="支付宝"
                width="82"
                height="29"
              >
            </template>
            <van-radio name="ali" />
          </van-cell>
          <van-cell>
            <template slot="title">
              <img
                src="../../../assets/images/wx_pay.png"
                alt="微信支付"
                width="113"
                height="23"
              >
            </template>
            <van-radio name="wx" />
          </van-cell>
        </van-cell-group>
      </van-radio-group>
    </div>

    <van-button
      class="pay_submit"
      type="primary"
      bottom-action
      @click="pay"
    >去支付</van-button>
  </div>
</template>

<script>
import { orderPrepay } from '@/api/pay'
import _ from 'lodash'
import { getLocalStorage } from '@/utils/localStorage'

export default {
  name: 'Payment',

  data() {
    return {
      payWay: '',
      orderSn: undefined,
      actualPrice: 0
    }
  },
  created() {
    if (_.has(this.$route.params, 'orderSn')) {
      this.orderSn = this.$route.params.orderSn
      this.actualPrice = this.$route.params.actualPrice
    }
  },
  methods: {
    buildAlipayReturnUrl() {
      const path = (window.location.pathname || '').split('#')[0]
      const base = path.replace(/\/+$/, '')
      return `${window.location.origin}${base}/alipay-return.html`
    },
    pay() {
      if (!this.payWay) {
        this.$toast.fail('请选择支付方式')
        return
      }
      if (this.payWay === 'wx') {
        // 微信h5支付
        const returnUrl = window.location.origin + window.location.pathname + '#/order/payStatus?status=success'
        orderPrepay({ orderSn: this.orderSn, payType: 5, returnUrl })
          .then((res) => {
            window.location.href = res.data.epayurl
          })
          .catch((err) => {
            console.log(err)
            // this.$dialog.alert({ message: '支付失败' })
            this.$router.replace({
              name: 'PayStatus',
              params: {
                status: 'failed'
              }
            })
          })
      } else if (this.payWay === 'ali') {
        // 支付宝官方手机网站支付（含沙箱）：PayTypeEnum.ALI_H5 = 2，后端返回 form 自动提交到支付宝网关
        this.$toast.loading({
          duration: 0, // 持续展示 toast
          forbidClick: true,
          message: '支付中，请稍后'
        })
        // 使用无 hash 的静态页作同步回跳，避免支付宝在 # 前后拼接参数导致无法回到 Vue 路由
        const returnUrl = this.buildAlipayReturnUrl()
        orderPrepay({ orderSn: this.orderSn, payType: 2, returnUrl })
          .then((res) => {
            this.$toast.clear()
            const formHtml = res.data && res.data.form
            if (!formHtml) {
              this.$router.replace({
                name: 'PayStatus',
                params: { status: 'failed' }
              })
              return
            }
            this.alipayClientCall(formHtml)
          })
          .catch((err) => {
            console.log(err)
            this.$toast.clear()
            this.$router.replace({
              name: 'PayStatus',
              params: {
                status: 'failed'
              }
            })
          })
      } else {
        // 测试支付
        this.$toast.loading({
          duration: 0, // 持续展示 toast
          forbidClick: true,
          message: '支付中，请稍后'
        })
        orderPrepay({ orderSn: this.orderSn, payType: 99 })
          .then((res) => {
            this.$router.replace({
              name: 'PayStatus',
              params: {
                status: 'success'
              }
            })
          })
          .catch((err) => {
            console.log(err)
            this.$router.replace({
              name: 'PayStatus',
              params: {
                status: 'failed'
              }
            })
          })
      }
    },
    /**
     * 通过提交form表单唤起支付宝客户端支付
     */
    alipayClientCall(form) {
      const div = document.createElement('div')
      div.style.display = 'none'
      div.innerHTML = form
      document.body.appendChild(div)
      const formEl = div.querySelector('form')
      if (formEl) {
        formEl.submit()
      } else if (document.forms.punchout_form) {
        document.forms.punchout_form.submit()
      } else {
        console.error('支付宝下单未返回可提交的表单')
      }
    },
    onBridgeReady() {
      const that = this
      const data = getLocalStorage('prepay_data')
      // eslint-disable-next-line no-undef
      WeixinJSBridge.invoke(
        'getBrandWCPayRequest',
        JSON.parse(data.prepay_data),
        function(res) {
          console.log(res)
          if (res.err_msg === 'get_brand_wcpay_request:ok') {
            that.$router.replace({
              name: 'PayStatus',
              params: {
                status: 'success'
              }
            })
          } else if (res.err_msg === 'get_brand_wcpay_request:cancel') {
            that.$router.replace({
              name: 'PayStatus',
              params: {
                status: 'cancel'
              }
            })
          } else {
            that.$router.replace({
              name: 'PayStatus',
              params: {
                status: 'failed'
              }
            })
          }
        }
      )
    }
  }
}
</script>

<style lang="scss" scoped>
$mb2vw: 2vw;
.payment {
  min-height: 100vh;
  background: #f5f5f5;

  .van-notice-bar {
    margin-bottom: $mb2vw;
  }

  .payment_group {
    margin-bottom: $mb2vw;
  }

  .pay_submit {
    position: fixed;
    bottom: 0;
    width: 100%;
  }

  .pay_way_group img {
    vertical-align: middle;
  }

  .pay_way_title {
    padding: 5vw 4vw;
    background-color: #fff;
  }
}
</style>
