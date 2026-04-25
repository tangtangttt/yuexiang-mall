package com.yuex.admin.api.controller.system;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yuex.admin.framework.security.util.SecurityUtils;
import com.yuex.common.annotation.Log;
import com.yuex.common.base.controller.BaseController;
import com.yuex.common.core.entity.system.User;
import com.yuex.common.core.service.system.IRoleService;
import com.yuex.common.core.service.system.IUserService;
import com.yuex.common.response.UserGetInfoResVO;
import com.yuex.util.constant.SysConstants;
import com.yuex.util.enums.ModuleEnum;
import com.yuex.util.enums.OperatorEnum;
import com.yuex.util.enums.ReturnCodeEnum;
import com.yuex.util.util.R;
import com.yuex.util.util.excel.ExcelUtil;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Date;
import java.util.List;
import java.util.Objects;

/**
 * 后台用户管理
 *
 * @author yuex
 * @since 2020-07-21
 */
@Slf4j
@RestController
@AllArgsConstructor
@RequestMapping("system/user")
public class UserController extends BaseController {

    private IUserService iUserService;

    private IRoleService iRoleService;

    /**
     * 用户列表
     *
     * @param user
     * @return
     */
    @Log(value = ModuleEnum.USER, operator = OperatorEnum.SELECT)
    @PreAuthorize("@ss.hasPermi('system:user:list')")
    @GetMapping("/list")
    public R<IPage<User>> list(User user) {
        Page<User> page = getPage();
        return R.success(iUserService.listPage(page, user));
    }

    /**
     * 获取用户信息
     *
     * @return
     */
    @PreAuthorize("@ss.hasPermi('system:user:query')")
    @GetMapping(value = {"/", "/{userId}"})
    public R<UserGetInfoResVO> getInfo(@PathVariable(value = "userId", required = false) Long userId) {
        UserGetInfoResVO resVO = new UserGetInfoResVO();
        resVO.setRoles(iRoleService.list());
        if (Objects.nonNull(userId)) {
            resVO.setRoleIds(iRoleService.selectRoleListByUserId(userId));
            resVO.setUser(iUserService.getById(userId));
        }
        return R.success(resVO);
    }

    /**
     * 添加用户
     *
     * @param user
     * @return
     */
    @PreAuthorize("@ss.hasPermi('system:user:add')")
    @PostMapping
    public R<Boolean> addUser(@Validated @RequestBody User user) {
        if (SysConstants.NOT_UNIQUE.equals(iUserService.checkUserNameUnique(user.getUserName()))) {
            return R.error(ReturnCodeEnum.CUSTOM_ERROR.setMsg(String.format("导入用户[%s]失败，登录账号已存在", user.getUserName())));
        } else if (SysConstants.NOT_UNIQUE.equals(iUserService.checkPhoneUnique(user))) {
            return R.error(ReturnCodeEnum.CUSTOM_ERROR.setMsg(String.format("导入用户[%s]失败，手机号码已存在", user.getUserName())));
        } else if (SysConstants.NOT_UNIQUE.equals(iUserService.checkEmailUnique(user))) {
            return R.error(ReturnCodeEnum.CUSTOM_ERROR.setMsg(String.format("导入用户[%s]失败，邮箱账号已存在", user.getUserName())));
        }
        user.setAvatar(SysConstants.DEFAULT_AVATAR);
        user.setCreateBy(SecurityUtils.getUsername());
        user.setCreateTime(new Date());
        user.setPassword(SecurityUtils.encryptPassword(user.getPassword()));
        return R.result(iUserService.insertUserAndRole(user));
    }

    /**
     * 修改用户
     *
     * @param user
     * @return
     */
    @PreAuthorize("@ss.hasPermi('system:user:update')")
    @PutMapping
    public R<Boolean> updateUser(@Validated @RequestBody User user) {
        iUserService.checkUserAllowed(user);
        if (SysConstants.NOT_UNIQUE.equals(iUserService.checkPhoneUnique(user))) {
            return R.error(ReturnCodeEnum.CUSTOM_ERROR.setMsg(String.format("导入用户[%s]失败，手机号码已存在", user.getUserName())));
        } else if (SysConstants.NOT_UNIQUE.equals(iUserService.checkEmailUnique(user))) {
            return R.error(ReturnCodeEnum.CUSTOM_ERROR.setMsg(String.format("导入用户[%s]失败，邮箱账号已存在", user.getUserName())));
        }
        user.setUpdateBy(SecurityUtils.getUsername());
        user.setUpdateTime(new Date());
        return R.result(iUserService.updateUserAndRole(user));
    }

    /**
     * 重置密码
     *
     * @param user
     * @return
     */
    @PreAuthorize("@ss.hasPermi('system:user:resetPwd')")
    @PutMapping("resetPwd")
    public R<Boolean> resetPwd(@RequestBody User user) {
        iUserService.checkUserAllowed(user);
        Long userId = SecurityUtils.getUserId();
        user.setUserId(userId);
        user.setPassword(SecurityUtils.encryptPassword(user.getPassword()));
        user.setUpdateBy(SecurityUtils.getUsername());
        user.setUpdateTime(new Date());
        return R.result(iUserService.updateById(user));
    }

    /**
     * 更新用户状态
     *
     * @param user
     * @return
     */
    @PreAuthorize("@ss.hasPermi('system:user:update')")
    @PutMapping("changeStatus")
    public R<Boolean> changeStatus(@RequestBody User user) {
        iUserService.checkUserAllowed(user);
        user.setUpdateBy(SecurityUtils.getUsername());
        return R.result(iUserService.updateById(user));
    }

    /**
     * 删除用户
     *
     * @param userIds
     * @return
     */
    @PreAuthorize("@ss.hasPermi('system:user:delete')")
    @DeleteMapping("/{userIds}")
    public R<Boolean> deleteUser(@PathVariable List<Long> userIds) {
        return R.result(iUserService.removeByIds(userIds));
    }

    /**
     * 用户导出
     *
     * @param user
     * @param response
     */
    @PreAuthorize("@ss.hasPermi('system:user:export')")
    @GetMapping("/export")
    public void export(User user, HttpServletResponse response) {
        List<User> list = iUserService.list(user);
        list.forEach(item -> item.setDeptName(item.getDept().getDeptName()));
        ExcelUtil.exportExcel(response, list, User.class, "用户数据.xlsx");
    }

    /**
     * 用户导入
     *
     * @param file
     * @return
     */
    @PreAuthorize("@ss.hasPermi('system:user:import')")
    @ResponseBody
    @PostMapping("/importData")
    public R importData(@RequestParam("file") MultipartFile file) {
        iUserService.importUser(file, SecurityUtils.getUsername(), SecurityUtils.encryptPassword(SysConstants.DEFAULT_PASSWORD));
        return R.success();
    }

}
