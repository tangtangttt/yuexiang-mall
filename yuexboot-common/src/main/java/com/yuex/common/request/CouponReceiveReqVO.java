package com.yuex.common.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * @author:yuex
 * @date*/
@Data
public class CouponReceiveReqVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 优惠卷id
     */
    @NotNull
    private Integer couponId;
}
