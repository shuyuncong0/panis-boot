/*
 * All Rights Reserved: Copyright [2024] [Zhuang Pan (paynezhuang@gmail.com)]
 * Open Source Agreement: Apache License, Version 2.0
 * For educational purposes only, commercial use shall comply with the author's copyright information.
 * The author does not guarantee or assume any responsibility for the risks of using software.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.izpan.modules.system.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.izpan.modules.system.domain.bo.SysRoleDataScopeQueryBO;
import com.izpan.modules.system.domain.entity.SysRoleDataScope;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * 角色数据权限关联管理 Mapper 接口层
 *
 * @Author payne.zhuang <paynezhuang@gmail.com>
 * @ProjectName panis-boot
 * @ClassName com.izpan.modules.system.repository.mapper.SysRoleDataScopeMapper
 * @CreateTime 2025-05-10 - 21:40:18
 */

public interface SysRoleDataScopeMapper extends BaseMapper<SysRoleDataScope> {

    /**
     * 根据权限编码查询数据权限
     *
     * @param permissionCode 权限编码
     * @return {@link List }<{@link SysRoleDataScopeQueryBO }> 数据权限配置
     * @author payne.zhuang
     * @CreateTime 2025-05-12 - 20:37:51
     */
    List<SysRoleDataScopeQueryBO> listByPermissionCode(String permissionCode);

    /**
     * 根据角色 ID 及数据权限 ID 集合查询已软删除的数据权限关联
     *
     * @param roleId       角色 ID
     * @param dataScopeIds 数据权限 ID 集合
     * @return {@link List }<{@link SysRoleDataScope }> 数据权限关联列表
     * @author payne.zhuang
     * @CreateTime 2025-12-22 - 21:05:08
     */
    List<SysRoleDataScope> listSoftDeletedByRoleId(Long roleId, Set<Long> dataScopeIds);

    /**
     * 根据角色 ID 和数据权限 ID 集合恢复软删除的数据权限关联
     *
     * @param roleId       角色 ID
     * @param dataScopeIds 数据权限 ID 集合
     * @param updateUser   更新人
     * @param updateUserId 更新人 ID
     * @param updateTime   更新时间
     * @return {@link int} 恢复结果
     * @author payne.zhuang
     * @CreateTime 2025-12-22 - 21:51:08
     */
    int recoverByRoleAndDataScope(Long roleId, Set<Long> dataScopeIds,
                                  String updateUser, Long updateUserId, LocalDateTime updateTime);
}
