package com.yuex.common.util;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yuex.util.constant.Constants;
import com.yuex.util.util.ServletUtils;

/**
 * @author:yuex
 * @date*/
public class PageUtil {

    /**
     * 获取分页对象
     *
     * @return 返回分页对象
     */
    public static <T> Page<T> getPage() {
        // 设置通用分页
        Integer pageNumber = ServletUtils.getParameterToInt(Constants.PAGE_NUMBER, "1");
        Integer pageSize = ServletUtils.getParameterToInt(Constants.PAGE_SIZE, "10");
        return new Page<>(pageNumber, pageSize);
    }
}
