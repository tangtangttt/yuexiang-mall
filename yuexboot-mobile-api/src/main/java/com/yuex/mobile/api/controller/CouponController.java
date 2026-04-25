package com.yuex.mobile.api.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yuex.common.base.controller.BaseController;
import com.yuex.common.core.entity.shop.ShopCoupon;
import com.yuex.common.core.service.shop.ShopCouponService;
import com.yuex.common.request.CouponReceiveReqVO;
import com.yuex.common.response.MemberCouponResVO;
import com.yuex.common.response.ShopCouponResVO;
import com.yuex.mobile.framework.security.util.MobileSecurityUtils;
import com.yuex.util.util.R;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 优惠券接口
 */
@Slf4j
@RestController
@AllArgsConstructor
@RequestMapping("coupon")
public class CouponController extends BaseController {

    private ShopCouponService shopCouponService;

    /**
     * 优惠券列表
     *
     * @return
     */
    @GetMapping("list")
    public R<IPage<ShopCouponResVO>> fontList() {
        Page<ShopCoupon> page = getPage();
        Long userId = MobileSecurityUtils.getUserId();
        return R.success(shopCouponService.fontList(page, userId));
    }

    /**
     * 用户领取优惠券
     *
     * @return
     */
    @PostMapping("receive")
    public R<Boolean> receive(@RequestBody @Validated CouponReceiveReqVO reqVO) {
        Long userId = MobileSecurityUtils.getUserId();
        return R.success(shopCouponService.receive(reqVO, userId));
    }

    /**
     * 我的优惠卷列表
     *
     * @return
     */
    @GetMapping("myList")
    public R<IPage<MemberCouponResVO>> myList() {
        Page<ShopCoupon> page = getPage();
        Long userId = MobileSecurityUtils.getUserId();
        return R.success(shopCouponService.myList(page, userId));
    }
}
