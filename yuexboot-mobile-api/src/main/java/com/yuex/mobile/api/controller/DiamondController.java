package com.yuex.mobile.api.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yuex.common.base.controller.BaseController;
import com.yuex.common.core.entity.shop.Diamond;
import com.yuex.common.core.entity.shop.Goods;
import com.yuex.common.core.service.shop.IDiamondService;
import com.yuex.common.design.strategy.diamond.context.DiamondJumpContext;
import com.yuex.common.design.strategy.diamond.strategy.DiamondJumpTypeInterface;
import com.yuex.common.response.DiamondGoodsResVO;
import com.yuex.util.util.R;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 金刚区接口
 *
 * @author yuex
 * @since 2024/1/15
 */
@RestController
@AllArgsConstructor
@RequestMapping("diamond")
public class DiamondController extends BaseController {

    private IDiamondService iDiamondService;

    private DiamondJumpContext diamondJumpContext;

    /**
     * 金刚区跳转
     *
     * @param diamondId 金刚区id
     * @return R
     */
    @GetMapping("getGoodsList")
    public R<DiamondGoodsResVO> getGoodsList(Long diamondId) {
        Page<Goods> page = getPage();
        Diamond diamond = iDiamondService.getById(diamondId);
        DiamondJumpTypeInterface instance = diamondJumpContext.getInstance(diamond.getJumpType());
        DiamondGoodsResVO resVO = instance.getGoods(page, diamond);
        return R.success(resVO);
    }
}
