package com.yuex.mobile.api.controller;

import com.yuex.common.core.entity.shop.Member;
import com.yuex.common.core.service.shop.IMemberService;
import com.yuex.common.request.ProfileRequestVO;
import com.yuex.common.request.UpdatePasswordReqVO;
import com.yuex.mobile.framework.security.LoginUserDetail;
import com.yuex.mobile.framework.security.service.TokenService;
import com.yuex.mobile.framework.security.util.MobileSecurityUtils;
import com.yuex.util.enums.ReturnCodeEnum;
import com.yuex.util.exception.BusinessException;
import com.yuex.util.util.R;
import com.yuex.util.util.ServletUtils;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.extern.slf4j.XSlf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Objects;

/**
 * 用户接口
 */
@Slf4j
@RestController
@AllArgsConstructor
@RequestMapping("user")
public class UserController {

    private TokenService tokenService;

    private IMemberService iMemberService;

    /**
     * 获取用户信息
     *
     * @return R
     */
    @GetMapping("info")
    public R<Member> getInfo() {

        log.info("【USER-INFO-DEBUG】=== /user/info 接口被调用 ===");
        log.info("【USER-INFO-DEBUG】请求头 - Authorization: {}", 
            ServletUtils.getRequest().getHeader("Authorization"));
        
        LoginUserDetail loginUser = tokenService.getLoginUser(ServletUtils.getRequest());
        log.info("【USER-INFO-DEBUG】获取到的loginUser: {}", loginUser != null ? "成功" : "null");
        
        if (loginUser == null) {
            log.error("【USER-INFO-DEBUG】loginUser为null，返回401");
        } else {
            log.info("【USER-INFO-DEBUG】用户ID: {}", loginUser.getMember().getId());
            log.info("【USER-INFO-DEBUG】用户名: {}", loginUser.getUsername());
        }
        
        return R.success(loginUser.getMember());
    }

    /**
     * 修改用户资料
     *
     * @param profileRequestVO 用户资料参数
     * @return R
     */
    @PostMapping("profile")
    public R<Boolean> updateProfile(@RequestBody ProfileRequestVO profileRequestVO) {
        String nickname = profileRequestVO.getNickname();
        Integer gender = profileRequestVO.getGender();
        String mobile = profileRequestVO.getMobile();
        String email = profileRequestVO.getEmail();
        LocalDate birthday = profileRequestVO.getBirthday();
        LoginUserDetail loginUser = tokenService.getLoginUser(ServletUtils.getRequest());
        Member member = loginUser.getMember();
        if (StringUtils.isNotBlank(nickname)) {
            member.setNickname(nickname);
        }
        if (Objects.nonNull(gender)) {
            member.setGender(gender);
        }
        if (StringUtils.isNotBlank(mobile)) {
            member.setMobile(mobile);
        }
        if (StringUtils.isNotBlank(email)) {
            member.setEmail(email);
        }
        if (Objects.nonNull(birthday)) {
            member.setBirthday(birthday);
        }
        loginUser.setMember(member);
        tokenService.refreshToken(loginUser);
        return R.result(iMemberService.updateById(member));
    }

    /**
     * 上传头像
     *
     * @param avatar 用户头像地址
     * @return R
     */
    @PostMapping("uploadAvatar")
    public R<Member> uploadAvatar(String avatar) {
        LoginUserDetail loginUser = tokenService.getLoginUser(ServletUtils.getRequest());
        Member member = loginUser.getMember();
        member.setAvatar(avatar);
        boolean update = iMemberService.lambdaUpdate()
                .set(Member::getAvatar, avatar)
                .eq(Member::getId, member.getId())
                .update();
        if (!update) {
            throw new BusinessException("上传头像失败");
        }
        loginUser.setMember(member);
        tokenService.refreshToken(loginUser);
        return R.success(member);
    }

    /**
     * 更新用户密码
     *
     * @param reqVO 更新参数
     * @return R
     */
    @PostMapping("updatePassword")
    public R<Member> updatePassword(@RequestBody @Validated UpdatePasswordReqVO reqVO) {
        LoginUserDetail loginUser = tokenService.getLoginUser(ServletUtils.getRequest());
        String oldPassword = reqVO.getOldPassword();
        if (!MobileSecurityUtils.matchesPassword(oldPassword, loginUser.getPassword())) {
            return R.error(ReturnCodeEnum.OLD_PASSWORD_NOT_EQUALS_ERROR);
        }
        if (!StringUtils.equalsIgnoreCase(reqVO.getPassword(), reqVO.getConfirmPassword())) {
            return R.error(ReturnCodeEnum.USER_TWO_PASSWORD_NOT_SAME_ERROR);
        }
        Member member = loginUser.getMember();
        member.setPassword(MobileSecurityUtils.encryptPassword(reqVO.getPassword()));
        boolean update = iMemberService.updateById(member);
        if (!update) {
            throw new BusinessException("修改密码失败");
        }
        loginUser.setMember(member);
        tokenService.refreshToken(loginUser);
        return R.success(member);
    }
}
