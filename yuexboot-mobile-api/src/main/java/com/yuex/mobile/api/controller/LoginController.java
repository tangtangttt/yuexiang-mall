package com.yuex.mobile.api.controller;

import cn.hutool.core.util.PhoneUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yuex.common.core.entity.shop.Member;
import com.yuex.common.core.entity.shop.ShopCoupon;
import com.yuex.common.core.entity.shop.ShopMemberCoupon;
import com.yuex.common.core.service.shop.IMemberService;
import com.yuex.common.core.service.shop.ShopCouponService;
import com.yuex.common.core.service.shop.ShopMemberCouponService;
import com.yuex.common.request.GenMobileCodeReqVO;
import com.yuex.common.response.CaptchaResVO;
import com.yuex.common.util.ProfileUtil;
import com.yuex.data.redis.constant.RedisKeyEnum;
import com.yuex.data.redis.manager.StringRedisCache;
import com.yuex.mobile.framework.security.LoginObj;
import com.yuex.mobile.framework.security.service.LoginService;
import com.yuex.mobile.framework.security.util.MobileSecurityUtils;
import com.yuex.util.constant.SysConstants;
import com.yuex.util.exception.BusinessException;
import com.yuex.util.util.IdUtil;
import com.yuex.util.util.R;
import com.wf.captcha.SpecCaptcha;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.sms4j.api.SmsBlend;
import org.dromara.sms4j.api.entity.SmsResponse;
import org.dromara.sms4j.core.factory.SmsFactory;
import org.dromara.sms4j.provider.enumerate.SupplierType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.List;
import java.util.Objects;

import static com.yuex.data.redis.constant.RedisKeyEnum.MOBILE_CODE_CACHE;
import static com.yuex.data.redis.constant.RedisKeyEnum.MOBILE_CODE_SEND_CACHE;
import static com.yuex.util.enums.ReturnCodeEnum.*;

/**
 * 登录相关接口
 *
 * @author yuex
 * @since 2024/1/15
 */
@Slf4j
@RestController
@AllArgsConstructor
public class LoginController {

    private LoginService loginService;
    private StringRedisCache stringRedisCache;
    private IMemberService iMemberService;
    private ProfileUtil profileUtil;
    private ShopCouponService shopCouponService;
    private ShopMemberCouponService shopMemberCouponService;

    /**
     * 用户登录
     *
     * @param loginObj 登录参数
     * @return R
     */
    @PostMapping("/login")
    public R<String> login(@RequestBody @Validated LoginObj loginObj) {
        log.info("Login request received: {}", loginObj);

        String mobile = loginObj.getMobile();
        String yzm = loginObj.getYzm();

        // Validate the verification code
        String codeKey = MOBILE_CODE_CACHE.getKey(mobile + "_" + yzm);
        String cachedCode = stringRedisCache.getCacheStringDirectly(codeKey);
        log.info("Validating verification code - key: {}, cached value: {}", codeKey, cachedCode);

       // 验证码校验逻辑改进
       if (cachedCode == null || !cachedCode.equals(yzm)) {
           log.warn("验证码校验失败 - 手机号: {}, 输入验证码: {}, 缓存验证码: {}", mobile, yzm, cachedCode);
           return R.error(YZM_ENTER_ERROR);
       }

        // Remove the verification code after successful validation
        stringRedisCache.deleteObject(codeKey);
        log.info("Verification code validated and removed - key: {}", codeKey);

        // Check if the user already exists
        Member member = iMemberService.getOne(Wrappers.lambdaQuery(Member.class).eq(Member::getMobile, mobile));
        if (member == null) {
            // Register a new user if not found
            member = new Member();
            member.setNickname("User" + System.currentTimeMillis() / 1000);
            member.setAvatar(SysConstants.DEFAULT_AVATAR);
            member.setMobile(mobile);
            member.setPassword(MobileSecurityUtils.encryptPassword(SysConstants.DEFAULT_PASSWORD));
            member.setCreateTime(new Date());
            iMemberService.save(member);

            // Assign registration coupons
            List<ShopCoupon> coupons = shopCouponService.lambdaQuery()
                    .eq(ShopCoupon::getType, 1)
                    .eq(ShopCoupon::getStatus, 1)
                    .gt(ShopCoupon::getExpireTime, new Date())
                    .list();
            for (ShopCoupon coupon : coupons) {
                ShopMemberCoupon shopMemberCoupon = new ShopMemberCoupon();
                shopMemberCoupon.setCouponId(coupon.getId());
                shopMemberCoupon.setUserId(Math.toIntExact(member.getId()));
                shopMemberCoupon.setMin(coupon.getMin());
                shopMemberCoupon.setDiscount(coupon.getDiscount());
                shopMemberCoupon.setTitle(coupon.getTitle());
                shopMemberCoupon.setUseStatus(0);
                shopMemberCoupon.setExpireTime(coupon.getExpireTime());
                shopMemberCoupon.setCreateTime(new Date());
                shopMemberCouponService.save(shopMemberCoupon);
            }
        }

        // Generate a token for the user
        String token = loginService.login(mobile, SysConstants.DEFAULT_PASSWORD);
        log.info("Login successful for mobile: {}, token: {}", mobile, token);

        // Return the token to the client
        return R.success(token);
    }
    /**
     * 验证码
     *
     * @return R
     */
    @ResponseBody
    @RequestMapping("/captcha")
    public R<CaptchaResVO> captcha() {
        // 创建验证码对象，定义验证码图形的长、宽、以及字数
        SpecCaptcha specCaptcha = new SpecCaptcha(80, 32, 4);
        // 生成验证码
        String verCode = specCaptcha.text().toLowerCase();
        // 生成验证码唯一key
        String key = RedisKeyEnum.CAPTCHA_KEY_CACHE.getKey(IdUtil.getUid());
        // 存入redis并设置过期时间为30分钟
        stringRedisCache.setCacheObject(key, verCode, RedisKeyEnum.CAPTCHA_KEY_CACHE.getExpireSecond());
        // 将key和base64返回给前端
        CaptchaResVO resVO = new CaptchaResVO();
        resVO.setKey(key);
        resVO.setImage(specCaptcha.toBase64());
        return R.success(resVO);
    }

    // 发送短信
    @RequestMapping("genMobileCode")
    public R<String> genMobileCode(@RequestBody @Validated GenMobileCodeReqVO reqVO) {
        log.info("genMobileCode req is {}", reqVO);
        String mobile = reqVO.getMobile();
        if (!PhoneUtil.isMobile(mobile)) {
            throw new BusinessException(MOBILE_ERROR);
        }
        Long ttl = stringRedisCache.ttl(MOBILE_CODE_SEND_CACHE.getKey(mobile));
        if (ttl > 0) {
            throw new BusinessException(YZM_ENTER_BUSY_ERROR);
        }
        String code = "1234";
        if (profileUtil.isProd()) {
            SmsBlend smsBlend = SmsFactory.createSmsBlend(SupplierType.ALIBABA);
            code = RandomUtil.randomNumbers(4);
            SmsResponse smsResponse = smsBlend.sendMessage(mobile, code);
            log.info("smsResponse is {}", smsResponse);
            if (!smsResponse.isSuccess()) {
                stringRedisCache.setCacheObject(MOBILE_CODE_SEND_CACHE.getKey(mobile), "1", MOBILE_CODE_SEND_CACHE.getExpireSecond());
                throw new BusinessException(MOBILE_YZM_SEND_ERROR.getCode(), smsResponse.getMessage());
            }
        }
        log.info("手机号：{}，发送短信：{}", mobile, code);
        String sendKey = MOBILE_CODE_SEND_CACHE.getKey(mobile);
        String codeKey = MOBILE_CODE_CACHE.getKey(mobile + "_" + code);
        stringRedisCache.setCacheObject(sendKey, "1", MOBILE_CODE_SEND_CACHE.getExpireSecond());
        stringRedisCache.setCacheString(codeKey, code, MOBILE_CODE_CACHE.getExpireSecond().longValue());
        log.info("验证码已存储 - sendKey: {}, codeKey: {}, code: {}", sendKey, codeKey, code);

        // 验证一下存储是否成功
        String storedValue = stringRedisCache.getCacheStringDirectly(codeKey);
        log.info("验证存储结果 - key: {}, stored value: {}", codeKey, storedValue);

        return R.success("验证码发送成功");
    }
}
