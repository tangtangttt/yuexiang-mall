package com.yuex.admin.api.controller.system;

import com.yuex.admin.framework.security.model.LoginUserDetail;
import com.yuex.admin.framework.security.service.TokenService;
import com.yuex.admin.framework.security.util.SecurityUtils;
import com.yuex.common.base.service.UploadService;
import com.yuex.common.config.yuexConfig;
import com.yuex.common.core.entity.system.User;
import com.yuex.common.core.service.system.IUserService;
import com.yuex.common.response.UserProfileResVO;
import com.yuex.util.enums.ReturnCodeEnum;
import com.yuex.util.util.R;
import com.yuex.util.util.ServletUtils;
import com.yuex.util.util.file.FileUploadUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * 个人信息
 *
 * @author yuex
 * @since 2020-07-21
 */
@RestController
@AllArgsConstructor
@RequestMapping("system/user/profile")
public class ProfileController {

    private IUserService iUserService;

    private TokenService tokenService;

    private UploadService uploadService;

    /**
     * 获取用户个人资料
     *
     * @return
     */
    @GetMapping
    public R<UserProfileResVO> profile() {
        LoginUserDetail loginUser = tokenService.getLoginUser(ServletUtils.getRequest());
        UserProfileResVO resVO = new UserProfileResVO();
        resVO.setUser(loginUser.getUser());
        resVO.setRoleGroup(iUserService.selectUserRoleGroup(loginUser.getUsername()));
        return R.success(resVO);
    }

    /**
     * 修改用户个人资料
     *
     * @param user
     * @return
     */
    @PreAuthorize("@ss.hasPermi('system:profile:update')")
    @PutMapping
    public R<Boolean> updateProfile(@RequestBody User user) {
        if (iUserService.updateById(user)) {
            LoginUserDetail loginUser = tokenService.getLoginUser(ServletUtils.getRequest());
            // 更新缓存用户信息
            loginUser.getUser().setNickName(user.getNickName());
            loginUser.getUser().setPhone(user.getPhone());
            loginUser.getUser().setEmail(user.getEmail());
            loginUser.getUser().setSex(user.getSex());
            tokenService.refreshToken(loginUser);
            return R.success();
        }
        return R.error();
    }

    /**
     * 更新用户密码
     *
     * @param oldPassword
     * @param newPassword
     * @return
     */
    @PreAuthorize("@ss.hasPermi('system:profile:update')")
    @PutMapping("/updatePwd")
    public R<Boolean> updatePwd(String oldPassword, String newPassword) {
        LoginUserDetail loginUser = tokenService.getLoginUser(ServletUtils.getRequest());
        String password = loginUser.getPassword();

        if (!SecurityUtils.matchesPassword(oldPassword, password)) {
            return R.error(ReturnCodeEnum.USER_OLD_PASSWORD_ERROR);
        }
        if (SecurityUtils.matchesPassword(newPassword, password)) {
            return R.error(ReturnCodeEnum.USER_NEW_OLD_PASSWORD_NOT_SAME_ERROR);
        }
        boolean result = iUserService.update().set("password", SecurityUtils.encryptPassword(newPassword)).update();
        if (result) {
            // 更新缓存用户信息
            loginUser.getUser().setPassword(SecurityUtils.encryptPassword(newPassword));
            tokenService.refreshToken(loginUser);
            return R.success();
        }
        return R.error();
    }

    /**
     * 用户上传头像
     *
     * @param file
     * @param request
     * @return
     * @throws IOException
     */
    @PreAuthorize("@ss.hasPermi('system:profile:update')")
    @PostMapping("/avatar")
    public R<String> avatar(@RequestParam("avatarfile") MultipartFile file, HttpServletRequest request) throws IOException {
        if (!file.isEmpty()) {
            LoginUserDetail loginUser = tokenService.getLoginUser(ServletUtils.getRequest());
            String filename = FileUploadUtil.uploadFile(file, yuexConfig.getUploadDir());
            String fileUrl = uploadService.uploadFile(filename);
            boolean result = iUserService.update().set("avatar", fileUrl).eq("user_name", loginUser.getUsername()).update();
            if (result) {
                // 更新缓存用户头像
                loginUser.getUser().setAvatar(fileUrl);
                tokenService.refreshToken(loginUser);
                return R.success(fileUrl);
            }
        }
        return R.error(ReturnCodeEnum.UPLOAD_ERROR);
    }
}
