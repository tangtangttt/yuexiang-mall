package com.yuex.common.core.mapper.shop;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yuex.common.core.entity.shop.Comment;
import com.yuex.common.core.vo.CommentTagNumVO;
import com.yuex.common.core.vo.CommentVO;

/**
 * 评论表 Mapper 接口
 *
 * @author yuex
 * @since 2020-10-03
 */
public interface CommentMapper extends BaseMapper<Comment> {

    IPage<Comment> selectListPage(Page<Comment> page, Comment comment);

    IPage<CommentVO> selectByTagType(Page<Comment> page, Long goodsId, Integer tagType);

    CommentTagNumVO selectTagNum(Long goodsId);
}
