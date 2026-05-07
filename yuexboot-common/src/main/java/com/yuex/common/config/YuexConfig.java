package com.yuex.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@ConfigurationProperties(prefix = "yuex")
public class YuexConfig {
    /**
     * 上传路径
     */
    private static String uploadDir;

    /**
     * 项目名称
     */
    private static String name;
    /**
     * 项目版本
     */
    private static String version;
    /**
     * 联系邮件
     */
    private static String email;

    /**
     * 管理后台地址
     */
    private static String adminUrl;
    /**
     * 商城移动端地址
     */
    private static String mobileUrl;

    /**
     * 未支付订单延时取消时间
     */
    private static Integer unpaidOrderCancelDelayTime;

    /**
     * 商城免运费限额
     */
    private static BigDecimal freightLimit;
    /**
     * 商城运费
     */
    private static BigDecimal freightPrice;

    public static String getUploadDir() {
        return uploadDir;
    }

    public void setUploadDir(String uploadDir) {
        YuexConfig.uploadDir = uploadDir;
    }

    public static String getDownloadPath() {
        return getUploadDir() + "/download/";
    }

    public static String getAvatarPath() {
        return getUploadDir() + "/avatar/";
    }

    public static String getName() {
        return name;
    }

    public void setName(String name) {
        YuexConfig.name = name;
    }

    public static String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        YuexConfig.version = version;
    }

    public static String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        YuexConfig.email = email;
    }

    public static String getAdminUrl() {
        return adminUrl;
    }

    public void setAdminUrl(String adminUrl) {
        YuexConfig.adminUrl = adminUrl;
    }

    public static String getMobileUrl() {
        return mobileUrl;
    }

    public void setMobileUrl(String mobileUrl) {
        YuexConfig.mobileUrl = mobileUrl;
    }

    public static BigDecimal getFreightLimit() {
        return freightLimit;
    }

    public void setFreightLimit(BigDecimal freightLimit) {
        YuexConfig.freightLimit = freightLimit;
    }

    public static BigDecimal getFreightPrice() {
        return freightPrice;
    }

    public void setFreightPrice(BigDecimal freightPrice) {
        YuexConfig.freightPrice = freightPrice;
    }

    public static Integer getUnpaidOrderCancelDelayTime() {
        return unpaidOrderCancelDelayTime;
    }

    public void setUnpaidOrderCancelDelayTime(Integer unpaidOrderCancelDelayTime) {
        YuexConfig.unpaidOrderCancelDelayTime = unpaidOrderCancelDelayTime;
    }
}
