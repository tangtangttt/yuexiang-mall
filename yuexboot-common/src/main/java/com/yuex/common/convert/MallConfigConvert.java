package com.yuex.common.convert;

import com.yuex.common.config.YuexConfig;
import com.yuex.common.response.MallConfigResponseVO;

/**
 * @author:yuex
 * @date*/
public class MallConfigConvert {

    public static MallConfigResponseVO convertMallConfig() {

        return MallConfigResponseVO.builder()
                .freightLimit(YuexConfig.getFreightLimit())
                .freightPrice(YuexConfig.getFreightPrice())
                .mobileUrl(YuexConfig.getMobileUrl())
                .email(YuexConfig.getEmail())
                .name(YuexConfig.getName())
                .unpaidOrderCancelDelayTime(YuexConfig.getUnpaidOrderCancelDelayTime())
                .version(YuexConfig.getVersion())
                .uploadDir(YuexConfig.getUploadDir())
                .build();
    }
}
