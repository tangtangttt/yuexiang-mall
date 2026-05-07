import request from '@/utils/request'

export function orderPrepay(data) {
  return request({
    url: '/pay/prepay',
    method: 'post',
    data
  })
}

/** 支付宝回跳后查单，将已支付结果写入订单 */
export function alipaySyncPaid(orderSn) {
  return request({
    url: '/pay/alipay/syncPaid',
    method: 'post',
    params: { orderSn }
  })
}
