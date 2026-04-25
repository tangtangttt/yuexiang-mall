package com.yuex.common.core.service.shop;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yuex.common.core.entity.shop.Goods;
import com.yuex.common.response.HomeIndexResponseVO;
import com.yuex.common.response.MallConfigResponseVO;

import java.util.List;

public interface IHomeService {

    /**
     * 获取首页数据（bannerList，category List，newGoodsList，hotGoodsList） <br>
     * 采用CompletableFuture方式
     *
     * @return r
     * @see <a href="https://www.cnblogs.com/cjsblog/p/9267163.html">https://www.cnblogs.com/cjsblog/p/9267163.html</a>
     */
    HomeIndexResponseVO index();

    /**
     * 获取商品分页列表
     *
     * @param page 分页对象
     * @return r
     */
    List<Goods> listGoodsPage(Page<Goods> page);

    /**
     * 商城配置
     *
     * @return r
     */
    MallConfigResponseVO mallConfig();
}
