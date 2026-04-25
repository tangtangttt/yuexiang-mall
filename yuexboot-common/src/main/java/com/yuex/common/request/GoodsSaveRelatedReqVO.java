package com.yuex.common.request;

import com.yuex.common.core.vo.GoodsAttributeVO;
import com.yuex.common.core.vo.GoodsProductVO;
import com.yuex.common.core.vo.GoodsSpecificationVO;
import com.yuex.common.core.vo.GoodsVO;
import jakarta.validation.Valid;
import lombok.Data;

@Data
public class GoodsSaveRelatedReqVO {

    @Valid
    GoodsVO goods;
    @Valid
    GoodsSpecificationVO[] specifications;
    @Valid
    GoodsAttributeVO[] attributes;
    @Valid
    GoodsProductVO[] products;
}
