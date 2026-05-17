package com.yuex.common.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * @author:yuex
 * @date*/
@Data
public class ShopCouponGiveUserReqVO implements Serializable {

    /**
     * 用户id
     */
    @NotNull
    private Integer userId;

    /**
     * 优惠卷id
     */
    @NotNull
    private Integer couponId;

}
