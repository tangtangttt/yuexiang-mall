package com.yuex.common.core.mapper.shop;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yuex.common.core.entity.shop.Category;

import java.util.List;

/**
 * 类目表 Mapper 接口
 *
 * @author yuex
 * @since 2020-06-26
 */
public interface CategoryMapper extends BaseMapper<Category> {

    List<Category> selectCategoryList(Category category);
}
