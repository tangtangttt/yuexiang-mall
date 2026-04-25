package com.yuex.common.convert;

import com.yuex.common.config.yuexConfig;
import com.yuex.common.response.MallConfigResponseVO;

/**
 * @author: yuexaqua
 * @date: 2023/11/13 23:10
 */
public class MallConfigConvert {

    public static MallConfigResponseVO convertMallConfig() {

        return MallConfigResponseVO.builder()
                .freightLimit(yuexConfig.getFreightLimit())
                .freightPrice(yuexConfig.getFreightPrice())
                .mobileUrl(yuexConfig.getMobileUrl())
                .email(yuexConfig.getEmail())
                .name(yuexConfig.getName())
                .unpaidOrderCancelDelayTime(yuexConfig.getUnpaidOrderCancelDelayTime())
                .version(yuexConfig.getVersion())
                .uploadDir(yuexConfig.getUploadDir())
                .build();
    }
}
