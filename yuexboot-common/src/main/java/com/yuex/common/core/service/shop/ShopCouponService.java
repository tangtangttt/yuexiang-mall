package com.yuex.common.core.service.shop;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.yuex.common.core.entity.shop.ShopCoupon;
import com.yuex.common.request.CouponReceiveReqVO;
import com.yuex.common.request.ShopCouponGiveUserReqVO;
import com.yuex.common.request.ShopCouponReqVO;
import com.yuex.common.response.MemberCouponResVO;
import com.yuex.common.response.ShopCouponResVO;

/**
 * @author Administrator
 * @description 针对表【shop_coupon(优惠券)】的数据库操作Service
 * @createDate 2024-06-06 10:26:11
 */
public interface ShopCouponService extends IService<ShopCoupon> {

    IPage<ShopCoupon> listPage(Page<ShopCoupon> page, ShopCouponReqVO reqVO);

    void giveUser(ShopCouponGiveUserReqVO reqVO);

    IPage<ShopCouponResVO> fontList(Page<ShopCoupon> page, Long userId);

    Boolean receive(CouponReceiveReqVO reqVO, Long userId);

    IPage<MemberCouponResVO> myList(Page<ShopCoupon> page, Long userId);
}
