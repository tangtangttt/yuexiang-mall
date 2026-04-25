package com.yuex.common.core.service.system.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yuex.common.core.entity.system.UserRole;
import com.yuex.common.core.mapper.system.UserRoleMapper;
import com.yuex.common.core.service.system.IUserRoleService;
import org.springframework.stereotype.Service;

@Service
public class UserRoleServiceImpl extends ServiceImpl<UserRoleMapper, UserRole> implements IUserRoleService {
}
