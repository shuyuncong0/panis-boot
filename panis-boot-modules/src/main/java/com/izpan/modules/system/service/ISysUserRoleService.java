package com.izpan.modules.system.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.izpan.infrastructure.page.PageQuery;
import com.izpan.modules.system.domain.bo.SysUserRoleBO;
import com.izpan.modules.system.domain.entity.SysUserRole;

import java.util.List;
import java.util.Set;

/**
 * 用户角色管理 Service 服务接口层
 *
 * @Author payne.zhuang <paynezhuang@gmail.com>
 * @ProjectName panis-boot
 * @ClassName com.izpan.modules.system.service.ISysUserRoleService
 * @CreateTime 2023-07-24
 */
public interface ISysUserRoleService extends IService<SysUserRole> {
    /**
     * 用户角色管理 - 分页查询
     *
     * @param pageQuery     分页对象
     * @param sysUserRoleBO BO 查询对象
     * @return {@link IPage} 分页结果
     * @author payne.zhuang
     * @CreateTime 2023-07-24 15:10
     */
    IPage<SysUserRole> listSysUserRolePage(PageQuery pageQuery, SysUserRoleBO sysUserRoleBO);

    /**
     * 根据用户ID查询用户角色 ID s列表
     *
     * @param userId 用户 ID
     * @return {@link List<Long>} 用户角色 ID s列表
     * @author payne.zhuang
     * @CreateTime 2023-08-07 17:50
     */
    List<Long> queryRoleIdsWithUserId(Long userId);

    /**
     * 根据用户 ID 查询用户角色 Code 列表
     *
     * @param userId 用户 ID
     * @return {@link List<String>} 用户角色 Code 列表
     * @author payne.zhuang
     * @CreateTime 2024-04-19 22:22
     */
    List<String> queryRoleCodesWithUserId(Long userId);

    /**
     * 更新用户角色
     *
     * @param userId  用户 ID
     * @param roleIds 角色 ID 列表
     * @return {@link Boolean} 更新结果
     * @author payne.zhuang
     * @CreateTime 2024-04-18 14:52
     */
    boolean updateUserRole(Long userId, List<Long> roleIds);

    /**
     * 根据角色 ID 集合查询关联的用户 ID 列表
     * <p>
     * 用于数据权限缓存清理，当角色权限配置变更时，
     * 需要清理拥有这些角色的所有用户的权限缓存
     * </p>
     *
     * @param roleIds 角色 ID 集合
     * @return 用户 ID 列表
     * @author payne.zhuang
     * @CreateTime 2025-06-02 - 23:50:00
     */
    List<Long> listUserIdsByRoleIds(Set<Long> roleIds);

    /**
     * 根据角色 ID 集合删除关联用户的用户角色缓存
     *
     * @param roleIds 角色 ID 集合
     * @author payne.zhuang
     * @CreateTime 2025-12-23 - 12:11:56
     */
    void deleteUserRoleCacheWithRoleIds(Set<Long> roleIds);
}
