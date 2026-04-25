package com.yuex.message.consumer;


import com.alibaba.fastjson.JSONObject;
import com.rabbitmq.client.Channel;
import com.yuex.data.redis.manager.StringRedisCache;
import com.yuex.message.consumer.api.MobileApi;
import com.yuex.message.core.constant.MQConstants;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static com.yuex.data.redis.constant.RedisKeyEnum.ORDER_CONSUMER_MAP;

@Slf4j
@Component
public class OrderPayConsumer {

    @Resource
    private StringRedisCache stringRedisCache;
    @Resource
    private MobileApi mobileApi;

    @RabbitListener(queues = MQConstants.ORDER_DIRECT_QUEUE)
    public void process(Channel channel, Message message) throws IOException {
        String body = new String(message.getBody());
        log.info("OrderPayConsumer 消费者收到消息: {}", body);
        String msgId = message.getMessageProperties().getCorrelationId();
        if (StringUtils.isBlank(msgId)) {
            msgId = message.getMessageProperties().getMessageId();
        }
        if (StringUtils.isBlank(msgId)) {
            msgId = "order_mq_" + Integer.toHexString(body.hashCode());
        }
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        String orderSn = null;
        try {
            JSONObject root = JSONObject.parseObject(body);
            JSONObject order = root.getJSONObject("order");
            if (order != null) {
                orderSn = order.getString("orderSn");
            }
        } catch (Exception e) {
            log.warn("解析下单消息体失败，将仅用 msgId 做幂等: {}", e.getMessage());
        }
        String dedupKey = StringUtils.isNotBlank(orderSn)
                ? ORDER_CONSUMER_MAP.getKey("sn:" + orderSn)
                : ORDER_CONSUMER_MAP.getKey("cid:" + msgId);
        long dedupTtlSeconds = StringUtils.isNotBlank(orderSn) ? 86400L : ORDER_CONSUMER_MAP.getExpireSecond();
        if (!stringRedisCache.setStringIfAbsent(dedupKey, "1", dedupTtlSeconds, TimeUnit.SECONDS)) {
            log.info("下单消息已处理或处理中，直接确认: dedupKey={}", dedupKey);
            channel.basicAck(deliveryTag, false);
            return;
        }
        try {
            mobileApi.submitOrder(body);
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            stringRedisCache.deleteObject(dedupKey);
            channel.basicNack(deliveryTag, false, true);
            log.error(e.getMessage(), e);
        }
    }
}
