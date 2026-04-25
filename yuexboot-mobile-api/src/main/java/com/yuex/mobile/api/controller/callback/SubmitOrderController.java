package com.yuex.mobile.api.controller.callback;

import com.alibaba.fastjson.JSON;
import com.yuex.common.core.service.shop.IMobileOrderService;
import com.yuex.data.redis.constant.RedisKeyEnum;
import com.yuex.data.redis.manager.StringRedisCache;
import com.yuex.message.core.dto.OrderDTO;
import com.yuex.util.exception.BusinessException;
import com.yuex.util.util.R;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 下单回调接口
 */
@Slf4j
@RestController
@AllArgsConstructor
@RequestMapping("callback/order")
public class SubmitOrderController {

    private IMobileOrderService iMobileOrderService;
    private StringRedisCache stringRedisCache;

    /**
     * 回调下单
     *
     * @param order 订单数据传输对象
     * @return R
     */
    @PostMapping("submit")
    public R submit(String order) {
        log.info("callback order request is {}", order);
        OrderDTO orderDTO = JSON.parseObject(order, OrderDTO.class);
        try {
            iMobileOrderService.submit(orderDTO);

            // 修改：存储标准JSON格式
            Map<String, Object> resultMap = new HashMap<>();
            resultMap.put("code", 200);
            resultMap.put("status", "success");
            resultMap.put("orderSn", orderDTO.getOrderSn());
            resultMap.put("message", "下单成功");

            stringRedisCache.setCacheObject(
                    RedisKeyEnum.ORDER_RESULT_KEY.getKey(orderDTO.getOrderSn()),
                    JSON.toJSONString(resultMap),  // 转换为JSON字符串
                    RedisKeyEnum.ORDER_RESULT_KEY.getExpireSecond()
            );
            return R.success();
        } catch (Exception e) {
            String errorMsg = "error";
            if (e instanceof BusinessException businessException) {
                errorMsg = businessException.getMsg();
            }

            // 错误时也存储JSON格式
            Map<String, Object> errorMap = new HashMap<>();
            errorMap.put("code", 500);
            errorMap.put("status", "error");
            errorMap.put("message", errorMsg);

            stringRedisCache.setCacheObject(
                    RedisKeyEnum.ORDER_RESULT_KEY.getKey(orderDTO.getOrderSn()),
                    JSON.toJSONString(errorMap),
                    RedisKeyEnum.ORDER_RESULT_KEY.getExpireSecond()
            );
            log.error(e.getMessage(), e);
            return R.error();
        }
    }
}