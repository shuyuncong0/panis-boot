package com.izpan.modules.system.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.izpan.infrastructure.page.PageQuery;
import com.izpan.modules.system.domain.bo.SysMenuBO;
import com.izpan.modules.system.domain.bo.SysRoleMenuBO;
import com.izpan.modules.system.domain.entity.SysRoleMenu;

import java.util.List;
import java.util.Set;

/**
 * 角色菜单管理 Service 服务接口层
 *
 * @Author payne.zhuang <paynezhuang@gmail.com>
 * @ProjectName panis-boot
 * @ClassName com.izpan.modules.system.domain.entity.SysRoleMenu
 * @CreateTime 2023-08-05
 */
public interface ISysRoleMenuService extends IService<SysRoleMenu> {
    /**
     * 角色菜单管理 - 分页查询
     *
     * @param pageQuery     分页对象
     * @param sysRoleMenuBO BO 查询对象
     * @return {@link IPage} 分页结果
     * @author payne.zhuang
     * @CreateTime 2023-08-05 15:10
     */
    IPage<SysRoleMenu> listSysRoleMenuPage(PageQuery pageQuery, SysRoleMenuBO sysRoleMenuBO);

    /**
     * 添加角色菜单
     *
     * @param sysRoleMenuBO 添加 BO 对象
     * @return {@link Boolean} 添加结果
     * @author payne.zhuang
     * @CreateTime 2024-01-26 15:49
     */
    boolean add(SysRoleMenuBO sysRoleMenuBO);

    /**
     * 保存角色 ID 及菜单 ID
     *
     * @param roleId  角色 ID
     * @param menuIds 菜单 Ids  集合
     * @return {@linkplain Boolean} 保存结果
     * @author payne.zhuang
     * @CreateTime 2024-04-17 11:55
     */
    boolean addMenuForRoleId(Long roleId, List<Long> menuIds);

    /**
     * 根据角色 ID 获取菜单 ID 集合
     *
     * @param roleId 角色 ID
     * @return {@linkplain List}菜单Id 集合
     * @author payne.zhuang
     * @CreateTime 2024-04-17 11:47
     */
    List<Long> queryMenuIdsWithRoleId(Long roleId);

    /**
     * 根据菜单 ID 获取角色 ID 集合
     *
     * @param menuId 菜单 ID
     * @return {@link List }<{@link Long }> 角色 ID 集合
     * @author payne.zhuang
     * @CreateTime 2025-04-03 - 10:21:30
     */
    List<Long> queryRoleIdsWithMenuId(Long menuId);

    /**
     * 根据角色 ID 查询菜单列表
     *
     * @param roleId 角色 ID
     * @return {@link List} 菜单列表
     * @author payne.zhuang
     * @CreateTime 2024-04-20 20:50
     */
    List<SysMenuBO> queryMenuListWithRoleId(Long roleId);

    /**
     * 根据角色 ID 列表查询角色菜单列表
     *
     * @param roleIds 角色 ID 列表
     * @return {@link List} 角色菜单列表
     * @author payne.zhuang
     * @CreateTime 2024-02-04 22:00
     */
    List<SysRoleMenuBO> queryMenuListWithRoleIds(List<Long> roleIds);

    /**
     * 删除角色菜单缓存
     *
     * @param menuId 菜单 ID
     * @author payne.zhuang
     * @CreateTime 2024-04-20 21:55
     */
    void deleteRoleMenuCacheWithMenuId(Long menuId);

    /**
     * 删除角色菜单缓存
     *
     * @param menuIds 菜单 ID 集合
     * @author payne.zhuang
     * @CreateTime 2025-12-23 - 10:52:49
     */
    void deleteRoleMenuCacheWithMenuIds(Set<Long> menuIds);
}
