package com.izpan.modules.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.extension.toolkit.Db;
import com.google.common.collect.Sets;
import com.izpan.common.constants.SystemCacheConstant;
import com.izpan.common.util.CollectionUtil;
import com.izpan.infrastructure.page.PageQuery;
import com.izpan.infrastructure.util.RedisUtil;
import com.izpan.modules.system.domain.bo.SysUserRoleBO;
import com.izpan.modules.system.domain.entity.SysUserRole;
import com.izpan.modules.system.repository.mapper.SysUserRoleMapper;
import com.izpan.modules.system.service.ISysRoleService;
import com.izpan.modules.system.service.ISysUserRoleService;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 用户角色管理 Service 服务接口实现层
 *
 * @Author payne.zhuang <paynezhuang@gmail.com>
 * @ProjectName panis-boot
 * @ClassName com.izpan.modules.system.service.impl.SysUserRoleServiceImpl
 * @CreateTime 2023-07-24
 */
@Service
@RequiredArgsConstructor
public class SysUserRoleServiceImpl extends ServiceImpl<SysUserRoleMapper, SysUserRole> implements ISysUserRoleService {

    @NonNull
    private ISysRoleService sysRoleService;

    @Override
    public IPage<SysUserRole> listSysUserRolePage(PageQuery pageQuery, SysUserRoleBO sysUserRoleBO) {
        return baseMapper.selectPage(pageQuery.buildPage(), new LambdaQueryWrapper<>());
    }

    @Override
    public List<Long> queryRoleIdsWithUserId(Long userId) {
        List<SysUserRole> sysUserRoleList = baseMapper.listUserRoleByUserId(userId);
        return sysUserRoleList.stream().map(SysUserRole::getRoleId).toList();
    }

    @Override
    public List<String> queryRoleCodesWithUserId(Long userId) {
        return sysRoleService.queryRoleCodesWithUserId(userId);
    }

    @Override
    public boolean updateUserRole(Long userId, List<Long> roleIds) {
        List<Long> originUserRoleIds = queryRoleIdsWithUserId(userId);
        // 处理数据
        Set<Long> roleIdSet = Sets.newHashSet(roleIds);
        // 处理结果
        AtomicBoolean saveResult = new AtomicBoolean(true);
        CollectionUtil.handleDifference(
                Sets.newHashSet(originUserRoleIds),
                roleIdSet,
                // 处理增加和删除的数据
                (addRoleIdSet, removeRoleIdSet) -> {
                    // 如有删除，则进行删除数据
                    if (!CollectionUtils.isEmpty(removeRoleIdSet)) {
                        LambdaQueryWrapper<SysUserRole> removeQueryWrapper = new LambdaQueryWrapper<SysUserRole>()
                                .eq(SysUserRole::getUserId, userId)
                                .in(SysUserRole::getRoleId, removeRoleIdSet);
                        baseMapper.delete(removeQueryWrapper);
                    }
                    // 进行新增数据
                    if (!CollectionUtils.isEmpty(addRoleIdSet)) {
                        List<SysUserRole> sysUserRoleList = addRoleIdSet.stream()
                                .map(roleId -> new SysUserRole(userId, roleId))
                                .toList();
                        saveResult.set(Db.saveBatch(sysUserRoleList));
                    }
                }
        );

        return saveResult.get();
    }

    @Override
    public List<Long> listUserIdsByRoleIds(Set<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return List.of();
        }

        // 查询拥有指定角色的用户 ID 列表
        LambdaQueryWrapper<SysUserRole> queryWrapper = new LambdaQueryWrapper<SysUserRole>()
                .in(SysUserRole::getRoleId, roleIds)
                .select(SysUserRole::getUserId);

        return baseMapper.selectList(queryWrapper).stream()
                .map(SysUserRole::getUserId)
                // 去重，因为一个用户可能拥有多个指定的角色
                .distinct()
                .toList();
    }

    @Override
    public void deleteUserRoleCacheWithRoleIds(Set<Long> roleIds) {
        // 根据角色 Ids 找出关联用户 Ids，进行用户路由缓存删除
        List<Long> userIds = listUserIdsByRoleIds(roleIds);
        // 删除用户路由缓存
        userIds.stream()
                .filter(userId -> RedisUtil.exists(SystemCacheConstant.userRouteKey(userId)))
                .forEach(userId -> RedisUtil.del(SystemCacheConstant.userRouteKey(userId)));
    }
}
