package com.yuex.common.core.mapper.shop;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yuex.common.core.entity.shop.Column;

/**
 * 首页栏目配置 Mapper 接口
 *
 * @author yuex
 * @since*/
public interface ColumnMapper extends BaseMapper<Column> {

    IPage<Column> selectColumnListPage(Page<Column> page, Column column);
}
