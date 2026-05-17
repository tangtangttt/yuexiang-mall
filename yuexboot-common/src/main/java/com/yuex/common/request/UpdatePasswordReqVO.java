package com.yuex.common.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * @author:yuex
 * @date*/
@Data
public class UpdatePasswordReqVO implements Serializable {
    @Serial
    private static final long serialVersionUID = -8567410355614947736L;

    /**
     * 旧密码
     */
    @NotBlank
    private String oldPassword;
    /**
     * 用户密码
     */
    @NotBlank
    private String password;

    /**
     * 重复密码
     */
    @NotBlank
    private String confirmPassword;

}
