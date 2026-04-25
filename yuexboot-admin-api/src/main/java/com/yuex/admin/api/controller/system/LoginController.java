package com.yuex.admin.api.controller.system;

import com.yuex.admin.framework.security.model.LoginObj;
import com.yuex.admin.framework.security.model.LoginUserDetail;
import com.yuex.admin.framework.security.service.LoginService;
import com.yuex.admin.framework.security.service.PermissionService;
import com.yuex.admin.framework.security.service.TokenService;
import com.yuex.common.core.entity.system.Menu;
import com.yuex.common.core.entity.system.User;
import com.yuex.common.core.service.system.IMenuService;
import com.yuex.common.core.vo.RouterVo;
import com.yuex.common.response.CaptchaResVO;
import com.yuex.common.response.UserInfoResVO;
import com.yuex.data.redis.manager.StringRedisCache;
import com.yuex.util.constant.SysConstants;
import com.yuex.util.enums.ReturnCodeEnum;
import com.yuex.util.util.IdUtil;
import com.yuex.util.util.R;
import com.wf.captcha.SpecCaptcha;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 用户登录
 *
 * @author yuex
 * @since 2020-07-21
 */
@RestController
@AllArgsConstructor
public class LoginController {
    private LoginService loginService;
    private TokenService tokenService;
    private PermissionService permissionService;
    private IMenuService iMenuService;
    private StringRedisCache stringRedisCache;

    /**
     * 用户登录
     *
     * @param loginObj
     * @return
     */
    @PostMapping("/login")
    public R login(@RequestBody @Validated LoginObj loginObj) {
        // 使用字符串专用方法读取验证码
        String redisCode = stringRedisCache.getCacheString(loginObj.getKey());
        // 判断验证码
        if (loginObj.getCode() == null || !redisCode.equals(loginObj.getCode().trim().toLowerCase())) {
            return R.error(ReturnCodeEnum.USER_VERIFY_CODE_ERROR);
        }
        // 删除验证码
        stringRedisCache.deleteObject(loginObj.getKey());
        // 生成令牌
        String token = loginService.login(loginObj.getUsername(), loginObj.getPassword());
        return R.success(token);
    }

    /**
     * 获取用户信息
     *
     * @param request
     * @return
     */
    @GetMapping("/getInfo")
    public R<UserInfoResVO> userInfo(HttpServletRequest request) {
        LoginUserDetail loginUser = tokenService.getLoginUser(request);
        User user = loginUser.getUser();
        Set<String> rolePermission = permissionService.getRolePermission(user);
        Set<String> menuPermission = permissionService.getMenuPermission(rolePermission);
        UserInfoResVO userInfoResVO = new UserInfoResVO();
        userInfoResVO.setUser(user);
        userInfoResVO.setRoles(rolePermission);
        userInfoResVO.setPermissions(menuPermission);
        return R.success(userInfoResVO);
    }

    /**
     * 获取用户路由菜单
     *
     * @param request
     * @return
     */
    @GetMapping("/getRouters")
    public R<List<RouterVo>> getRouters(HttpServletRequest request) {
        LoginUserDetail loginUser = tokenService.getLoginUser(request);
        // 用户信息
        User user = loginUser.getUser();
        List<Menu> menus = iMenuService.selectMenuTreeByUserId(user.getUserId());
        return R.success(iMenuService.buildMenus(menus));
    }

    /**
     * 验证码
     *
     * @return
     */
    @GetMapping("/captcha")
    public R<CaptchaResVO> captcha() {
        SpecCaptcha specCaptcha = new SpecCaptcha(100, 43, 4);
        String verCode = specCaptcha.text().toLowerCase();
        String key = IdUtil.getUid();

        // SysConstants.CAPTCHA_EXPIRATION 是 Integer 类型，需要转换为 Long
        stringRedisCache.setCacheString(key, verCode, SysConstants.CAPTCHA_EXPIRATION.longValue(), TimeUnit.MINUTES);

        CaptchaResVO captchaResVO = new CaptchaResVO();
        captchaResVO.setImage(specCaptcha.toBase64());
        captchaResVO.setKey(key);
        return R.success(captchaResVO);
    }
}
