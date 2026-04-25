package com.yuex.common.core.mapper.shop;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yuex.common.core.entity.shop.Channel;

import java.util.List;

public interface ChannelMapper extends BaseMapper<Channel> {

    List<Channel> selectChannelList(Channel channel);
}
