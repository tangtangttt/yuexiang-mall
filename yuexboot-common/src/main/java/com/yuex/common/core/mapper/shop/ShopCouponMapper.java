package com.yuex.common.core.mapper.shop;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yuex.common.core.entity.shop.ShopCoupon;
import com.yuex.common.request.ShopCouponReqVO;
import com.yuex.common.response.MemberCouponResVO;
import com.yuex.common.response.ShopCouponResVO;

/**
 * @author Administrator
 * @description 针对表【shop_coupon(优惠券)】的数据库操作Mapper
 * @createDate 2024-06-06 10:26:11
 * @Entity generator.domain.ShopCoupon
 */
public interface ShopCouponMapper extends BaseMapper<ShopCoupon> {

    IPage<ShopCoupon> selectCouponListPage(Page<ShopCoupon> page, ShopCouponReqVO reqVO);

    IPage<ShopCouponResVO> fontList(Page<ShopCoupon> page);

    IPage<MemberCouponResVO> myList(Page<ShopCoupon> page, Long userId);

    void updateReceiveNum(Integer couponId);
}




