package com.yuex.mobile.api.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yuex.common.base.controller.BaseController;
import com.yuex.common.core.entity.shop.Goods;
import com.yuex.common.core.service.shop.IHomeService;
import com.yuex.common.response.HomeIndexResponseVO;
import com.yuex.common.response.MallConfigResponseVO;
import com.yuex.util.util.R;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 首页接口
 */
@RestController
@AllArgsConstructor
@RequestMapping("home")
public class HomeController extends BaseController {

    private IHomeService iHomeService;

    /**
     * 商城首页
     *
     * @return R
     */
    @GetMapping("index")
    public R<HomeIndexResponseVO> index() {
        return R.success(iHomeService.index());
    }

    /**
     * 获取商城配置
     *
     * @return R
     */
    @GetMapping("mallConfig")
    public R<MallConfigResponseVO> mallConfig() {
        return R.success(iHomeService.mallConfig());
    }

    /**
     * 为你推荐商品列表
     *
     * @return R
     */
    @GetMapping("recommonGoodsList")
    public R<List<Goods>> recommonGoodsList() {
        Page<Goods> page = getPage();
        return R.success(iHomeService.listGoodsPage(page));
    }
}

