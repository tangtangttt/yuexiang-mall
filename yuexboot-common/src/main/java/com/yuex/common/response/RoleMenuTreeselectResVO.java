package com.yuex.common.response;

import com.yuex.common.core.vo.TreeVO;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * @author: yuexaqua
 * @date: 2024/4/27 15:41
 */
@Data
public class RoleMenuTreeselectResVO implements Serializable {
    @Serial
    private static final long serialVersionUID = 3661585610363519365L;

    /**
     * 菜单树
     */
    private List<TreeVO> menuTree;
    /**
     * 已选中菜单id
     */
    private List<Long> checkedKeys;

}
