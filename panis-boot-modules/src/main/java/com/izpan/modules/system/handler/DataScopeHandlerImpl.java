/*
 * All Rights Reserved: Copyright [2025] [Zhuang Pan (paynezhuang@gmail.com)]
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

package com.izpan.modules.system.handler;

import com.baomidou.mybatisplus.core.plugins.IgnoreStrategy;
import com.baomidou.mybatisplus.core.plugins.InterceptorIgnoreHelper;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.izpan.common.pool.StringPools;
import com.izpan.common.util.CollectionUtil;
import com.izpan.infrastructure.context.DataScopeConditionContext;
import com.izpan.infrastructure.holder.DataScopeHolder;
import com.izpan.infrastructure.holder.GlobalUserHolder;
import com.izpan.infrastructure.util.GsonUtil;
import com.izpan.infrastructure.util.TimerUtil;
import com.izpan.modules.system.domain.bo.SysRoleDataScopeQueryBO;
import com.izpan.modules.system.service.ISysDataScopeService;
import com.izpan.starter.database.mybatis.plus.domain.DataScope;
import com.izpan.starter.database.mybatis.plus.domain.DataScopeCondition;
import com.izpan.starter.database.mybatis.plus.enums.DataScopeTypeEnum;
import com.izpan.starter.database.mybatis.plus.enums.QueryConditionsEnum;
import com.izpan.starter.database.mybatis.plus.handler.IDataScopeHandler;
import com.izpan.starter.database.mybatis.plus.resolver.DataScopeVariableResolver;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.mapping.MappedStatement;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.util.*;

/**
 * 数据权限处理器实现类
 * <p>
 * 主要功能：
 * 1. 与 DataScopeInterceptor 配合，负责获取权限信息和缓存管理
 * 2. 根据用户角色和权限配置，动态生成数据权限SQL条件
 * 3. 支持多种权限类型：全部、本人、本组织、本组织及下级、本人及下级、自定义
 * 4. 提供高效的缓存机制，避免重复计算权限
 * 5. 支持自定义权限规则的变量替换和SQL格式化
 * </p>
 *
 * @Author payne.zhuang <paynezhuang@gmail.com>
 * @ProjectName panis-boot
 * @ClassName com.izpan.modules.system.handler.DataScopeHandlerImpl
 * @CreateTime 2025/5/12 - 11:24
 */
@Slf4j
@Service
public class DataScopeHandlerImpl implements IDataScopeHandler {

    // ================================ 私有属性 ================================

    /**
     * 角色数据权限服务提供者，使用ObjectProvider延迟加载避免循环依赖
     */
    private final ObjectProvider<ISysDataScopeService> dataScopeServiceProvider;

    // ================================ 构造器和初始化 ================================

    /**
     * 构造器注入，初始化服务提供者
     *
     * @param dataScopeServiceObjectProvider 数据权限服务提供者
     * @author payne.zhuang
     * @CreateTime 2025-05-12 - 11:25
     */
    public DataScopeHandlerImpl(ObjectProvider<ISysDataScopeService> dataScopeServiceObjectProvider) {
        this.dataScopeServiceProvider = dataScopeServiceObjectProvider;
    }

    // ================================ IDataScopeHandler 接口实现 ================================

    /**
     * 获取权限标识，从数据权限上下文获取
     *
     * @return 权限标识，可能为 null
     * @author payne.zhuang
     * @CreateTime 2025-05-12 - 11:27
     */
    @Override
    public String getPermissionCode() {
        return DataScopeHolder.getPermissionCode();
    }

    /**
     * 获取当前用户 ID
     *
     * @return 用户 ID
     * @author payne.zhuang
     * @CreateTime 2025-05-12 - 11:28
     */
    @Override
    public Long getCurrentUserId() {
        return GlobalUserHolder.getUserId();
    }

    /**
     * 获取数据权限，优先从缓存获取，未命中则计算
     * <p>
     * 核心方法：根据权限标识获取用户的数据权限配置，
     * 支持缓存机制以提高性能，失败时返回默认权限
     * </p>
     *
     * @param ms             MyBatis 映射语句对象
     * @param permissionCode 权限标识
     * @return {@link DataScope} 数据权限对象
     * @author payne.zhuang
     * @CreateTime 2025-05-12 - 11:29
     */
    @Override
    public DataScope getDataScope(MappedStatement ms, String permissionCode) {
        if (!StringUtils.hasText(permissionCode)) {
            log.warn("[DataScope] 权限标识为空, 无需处理数据权限");
            return null;
        }

        Long userId = getCurrentUserId();
        TimerUtil.Timer timer = TimerUtil.start();

        try {
            return calculateDataScopeForUser(userId, permissionCode);
        } catch (Exception e) {
            log.error("[DataScope] 用户 ID={}, 权限码={}, msId={} - 权限获取失败, 耗时={}ms, 错误={}",
                    userId, permissionCode, ms.getId(), timer.duration(), e.getMessage(), e);
            return createSafeUnknownDataScope(permissionCode);
        }
    }

    // ================================ 权限计算核心方法 ================================

    /**
     * 计算指定用户和权限的数据权限
     * <p>
     * 核心逻辑：
     * 1. 查询用户的角色权限配置
     * 2. 过滤用户角色，确定基础权限类型
     * 3. 根据权限类型获取用户ID集合
     * 4. 处理自定义权限条件
     * 5. 构建最终的数据权限对象
     * </p>
     *
     * @param userId         用户 ID
     * @param permissionCode 权限标识
     * @return 数据权限对象
     * @author payne.zhuang
     * @CreateTime 2025-05-12 - 11:37
     */
    private DataScope calculateDataScopeForUser(Long userId, String permissionCode) {
        String calculationId = UUID.randomUUID().toString().substring(0, 8);
        log.info("[DataScope] 用户ID={} 权限码={} calculationId={} - 开始计算权限", userId, permissionCode, calculationId);
        TimerUtil.Timer totalTimer = TimerUtil.start();

        try {
            // 获取服务实例
            ISysDataScopeService service = dataScopeServiceProvider.getIfAvailable();
            if (service == null) {
                log.error("[DataScope] 用户ID={} 权限码={} calculationId={} - 数据权限服务不可用, 返回安全降级", userId, permissionCode, calculationId);
                return createSafeUnknownDataScope(permissionCode);
            }

            // 设置忽略数据权限，避免递归调用
            InterceptorIgnoreHelper.handle(IgnoreStrategy.builder().dataPermission(true).build());

            // 查询角色权限配置
            TimerUtil.Timer queryTimer = TimerUtil.start();
            List<SysRoleDataScopeQueryBO> roleDataScopes = service.listByPermissionResource(permissionCode);
            if (roleDataScopes.isEmpty()) {
                log.debug("[DataScope] 用户ID={} 权限码={} calculationId={} - 无角色权限配置, 返回全量默认权限, 总耗时={}ms",
                        userId, permissionCode, calculationId, totalTimer.duration());
                return createFullAccessDataScope(permissionCode);
            }
            log.debug("[DataScope] 用户ID={} 权限码={} calculationId={} - 角色权限配置查询完成, 配置角色数={}, 耗时={}ms",
                    userId, permissionCode, calculationId, roleDataScopes.size(), queryTimer.duration());

            // 过滤用户角色，只保留用户拥有的角色权限
            Set<Long> userRoleIds = GlobalUserHolder.getRoleIds();
            List<SysRoleDataScopeQueryBO> filteredScopes = roleDataScopes.stream()
                    .filter(item -> userRoleIds.contains(item.getRoleId())).toList();
            if (filteredScopes.isEmpty()) {
                log.debug("[DataScope] 用户ID={} 权限码={} calculationId={} - 用户无匹配角色权限, 返回全量默认权限, 总耗时={}ms",
                        userId, permissionCode, calculationId, totalTimer.duration());
                return createFullAccessDataScope(permissionCode);
            }
            log.debug("[DataScope] 用户ID={} 权限码={} calculationId={} - 用户角色权限过滤完成, 用户角色={}, 匹配数={}",
                    userId, permissionCode, calculationId, GsonUtil.toJson(userRoleIds), filteredScopes.size());

            // 确定权限类型
            DataScopeTypeEnum scopeType = determineBaseScopeType(filteredScopes);
            log.debug("[DataScope] 用户ID={} 权限码={} calculationId={} - 确定权限类型完成, 权限类型={}",
                    userId, permissionCode, calculationId, scopeType);

            // 根据权限类型获取用户 ID 集合
            TimerUtil.Timer userIdsTimer = TimerUtil.start();
            Set<Long> scopeUserIds = service.getUserIdsByScopeType(userId, scopeType);
            log.debug("[DataScope] 用户ID={} 权限码={} calculationId={} - 权限用户集合获取完成, 权限类型={}, 包含用户={}, 耗时={}ms",
                    userId, permissionCode, calculationId, scopeType, GsonUtil.toJson(scopeUserIds), userIdsTimer.duration());

            // 自定义权限条件处理
            TimerUtil.Timer customTimer = TimerUtil.start();
            List<DataScopeCondition> customConditions = processCustomScopes(filteredScopes);
            log.debug("[DataScope] 用户ID={} 权限码={} calculationId={} - 自定义权限条件处理完成, 条件={}, 耗时={}ms",
                    userId, permissionCode, calculationId, GsonUtil.toJson(customConditions), customTimer.duration());

            // 构建权限对象
            TimerUtil.Timer buildTimer = TimerUtil.start();
            DataScope dataScope = buildDataScope(service, scopeType, customConditions, userId, scopeUserIds, permissionCode);
            log.debug("[DataScope] 用户ID={} 权限码={} calculationId={} - 权限对象构建完成, 耗时={}ms",
                    userId, permissionCode, calculationId, buildTimer.duration());

            log.info("[DataScope] 用户ID={} 权限码={} calculationId={} - 权限计算完成, 权限类型={}, 包含用户={}, 自定义条件={}, 总耗时={}ms",
                    userId, permissionCode, calculationId, dataScope.getScopeType(), GsonUtil.toJson(scopeUserIds), GsonUtil.toJson(customConditions), totalTimer.duration());
            return dataScope;
        } catch (Exception e) {
            log.error("[DataScope] 用户ID={} 权限码={} calculationId={} - 权限计算失败, 总耗时={}ms, 错误={}",
                    userId, permissionCode, calculationId, totalTimer.duration(), e.getMessage(), e);
            return createSafeUnknownDataScope(permissionCode);
        } finally {
            // 关闭忽略策略
            InterceptorIgnoreHelper.clearIgnoreStrategy();
        }
    }


    /**
     * 确定基础权限类型，异常时则会返回 UN_KNOWN 类型
     * <p>
     * 优先级：ALL > UNIT_AND_CHILD > UNIT > SELF_AND_CHILD > SELF
     * </p>
     *
     * @param roleDataScopes 角色权限列表
     * @return {@link DataScopeTypeEnum} 权限类型
     * @author payne.zhuang
     * @CreateTime 2025-05-13 - 21:48
     */
    private DataScopeTypeEnum determineBaseScopeType(List<SysRoleDataScopeQueryBO> roleDataScopes) {
        return roleDataScopes.stream()
                .map(scope -> DataScopeTypeEnum.safeSqlOf(scope.getScopeType()))
                .min(Comparator.comparing(DataScopeTypeEnum::getPriority))
                .orElse(DataScopeTypeEnum.UN_KNOWN);
    }

    /**
     * 处理自定义权限条件
     * <p>
     * 从用户的所有角色数据权限中，筛选出CUSTOM类型的权限， <br>
     * 解析每个CUSTOM权限的customRules字段，转换为DataScopeCondition对象列表， 最后合并所有CUSTOM权限的条件列表
     * </p>
     *
     * @param roleDataScopes 角色数据权限列表
     * @return 自定义条件列表
     * @author payne.zhuang
     * @CreateTime 2025-05-13 - 21:49
     */
    private List<DataScopeCondition> processCustomScopes(List<SysRoleDataScopeQueryBO> roleDataScopes) {
        return roleDataScopes.stream()
                .filter(scope -> DataScopeTypeEnum.CUSTOM.getType().equals(scope.getScopeType()))
                .map(this::parseCustomScope)
                .flatMap(Collection::stream)
                .toList();
    }

    /**
     * 解析自定义权限条件
     * <p>
     * 将JSON格式的customRules转换为DataScopeCondition对象列表，解析失败时返回空列表，确保系统稳定性
     * </p>
     *
     * @param scope 角色数据权限
     * @return 数据权限条件列表
     * @author payne.zhuang
     * @CreateTime 2025-05-13 - 21:50
     */
    private List<DataScopeCondition> parseCustomScope(SysRoleDataScopeQueryBO scope) {
        if (ObjectUtils.isEmpty(scope.getCustomRules())) {
            return Collections.emptyList();
        }
        try {
            List<DataScopeCondition> conditions = new Gson()
                    .fromJson(scope.getCustomRules(), new TypeToken<List<DataScopeCondition>>() {
                    }.getType());
            if (conditions == null || conditions.isEmpty()) {
                log.error("[DataScope] 自定义规则解析为空, customRules={}", scope.getCustomRules());
                return Collections.emptyList();
            }
            return conditions;
        } catch (Exception e) {
            log.error("[DataScope] 自定义规则解析失败, customRules={}, 错误={}", scope.getCustomRules(), e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    // ================================ 数据权限对象构建方法 ================================

    /**
     * 构建数据权限对象
     * <p>
     * 支持自定义条件中的变量占位符（如#{currentUserId}）自动替换为实际值，根据操作符正确格式化SQL，构建失败时保持基础权限类型
     * </p>
     *
     * @param service          数据权限服务
     * @param baseScopeType    基础权限类型
     * @param customConditions 自定义条件列表
     * @param userId           用户 ID
     * @param scopeUserIds     权限用户 ID 列表
     * @param permissionCode   权限编码
     * @return {@link DataScope} 数据权限对象
     * @author payne.zhuang
     * @CreateTime 2025-05-13 - 21:50
     */
    private DataScope buildDataScope(ISysDataScopeService service, DataScopeTypeEnum baseScopeType,
                                     List<DataScopeCondition> customConditions, Long userId,
                                     Set<Long> scopeUserIds, String permissionCode) {
        // 构建基础数据权限对象
        DataScope dataScope = DataScope.builder()
                .scopeType(baseScopeType)
                .currentUserId(userId)
                .scopeUserIds(scopeUserIds)
                .permissionCode(permissionCode)
                .build();

        // 处理自定义条件
        if (CollectionUtil.isNotEmpty(customConditions)) {
            String customRules = buildCustomRules(service, customConditions, userId);
            if (StringUtils.hasText(customRules)) {
                dataScope.setScopeType(DataScopeTypeEnum.CUSTOM);
                dataScope.setCustomRules(customRules);
            }
        }
        return dataScope;
    }

    /**
     * 构建自定义规则 SQL
     * <p>
     * 核心功能：<br>
     * 1. 构建数据权限变量上下文，获取变量的实际值 <br>
     * 2. 遍历条件列表，为每个条件添加逻辑连接符 <br>
     * 3. 使用 DataScopeVariableResolver 进行变量替换和格式化 <br>
     * 4. 根据操作符类型决定是否添加条件值 <br>
     * 5. 构建完整的 SQL WHERE 条件，自动处理 AND/OR 混合情况
     * </p>
     * <p>
     * 核心逻辑： <br>
     * 1. 只要有 OR，必须用括号包裹所有自定义条件，确保安全 <br>
     * 原因：如果没有括号，SQL 会是 WHERE is_deleted = 0 AND A OR B <br>
     * 由于 AND 优先级高于 OR，实际执行是 WHERE (is_deleted = 0 AND A) OR B <br>
     * 这会包含 is_deleted = 1 的数据，不安全！ <br>
     * 正确应该是：WHERE is_deleted = 0 AND (A OR B) <br>
     * 2. 如果只有 AND，直接拼接（不需要括号）
     * </p>
     *
     * @param service          数据权限服务
     * @param customConditions 自定义条件列表
     * @param userId           用户 ID
     * @return {@linkplain String} 自定义规则 SQL 字符串
     * @author payne.zhuang
     * @CreateTime 2025-05-13 - 21:51
     */
    private String buildCustomRules(ISysDataScopeService service, List<DataScopeCondition> customConditions, Long userId) {
        try {
            // 构建数据权限上下文，获取变量值
            List<DataScopeConditionContext> conditionContexts = service.buildDataScopeVariableValue(userId, customConditions);

            if (conditionContexts.isEmpty()) {
                return StringPools.EMPTY;
            }

            // 使用 StringBuilder 构建 SQL，便于打印测试
            StringBuilder sqlBuilder = new StringBuilder();

            // 如果只有一个条件，直接构建（不需要括号）
            if (conditionContexts.size() == 1) {
                buildSingleCondition(sqlBuilder, conditionContexts.getFirst(), userId);
                String customRules = sqlBuilder.toString();
                log.debug("[DataScope] 用户ID={} - 自定义规则构建成功, 条件数量={}, 规则={}", userId, conditionContexts.size(), customRules);
                return customRules;
            }

            // 检测是否有 OR（从第二个条件开始检查，因为第一个条件没有 logic）
            // 只要有 OR，就必须用括号包裹所有自定义条件，确保安全
            boolean hasOr = conditionContexts.stream()
                    .skip(1)
                    .anyMatch(ctx -> "OR".equalsIgnoreCase(ctx.getLogic()));

            if (hasOr) {
                // 有 OR，用括号包裹所有条件
                // 确保最终 SQL 是：WHERE is_deleted = 0 AND (自定义条件)
                // 而不是：WHERE is_deleted = 0 AND 自定义条件（可能不安全）
                sqlBuilder.append(StringPools.LEFT_BRACKET);
                buildSimpleConditions(sqlBuilder, conditionContexts, userId);
                sqlBuilder.append(StringPools.RIGHT_BRACKET);
            } else {
                // 只有 AND，直接拼接（不需要括号）
                // 最终 SQL 是：WHERE is_deleted = 0 AND A AND B
                buildSimpleConditions(sqlBuilder, conditionContexts, userId);
            }

            String customRules = sqlBuilder.toString();
            log.debug("[DataScope] 用户ID={} - 自定义规则构建成功, 条件数量={}, 规则={}", userId, conditionContexts.size(), customRules);
            return customRules;
        } catch (Exception e) {
            log.error("[DataScope] 用户ID={} - 自定义规则构建失败, 错误={}", userId, e.getMessage(), e);
            // 构建失败时返回空字符串
            return StringPools.EMPTY;
        }
    }

    // ================================ 构建自定义条件 ================================

    /**
     * 构建单个条件，追加到 StringBuilder
     * <p>
     * 格式：字段名 + 操作符 + 值
     * 示例：create_user_id = 1928267764836782081
     * </p>
     *
     * @param sqlBuilder StringBuilder 对象
     * @param context    条件上下文
     * @param userId     用户 ID
     */
    private void buildSingleCondition(StringBuilder sqlBuilder, DataScopeConditionContext context, Long userId) {
        QueryConditionsEnum operator = context.getConditionsEnum();
        String resolvedValue = DataScopeVariableResolver.resolveVariables(
                userId, context.getValue(), context.getVariableValue(), operator);

        sqlBuilder.append(context.getField())
                .append(StringPools.SPACE)
                .append(operator.getSqlOperator());

        // IS_NULL 和 IS_NOT_NULL 不需要值
        if (!QueryConditionsEnum.IS_NULL.equals(operator) &&
                !QueryConditionsEnum.IS_NOT_NULL.equals(operator)) {
            sqlBuilder.append(StringPools.SPACE).append(resolvedValue);
        }
    }

    /**
     * 构建简单条件（按顺序拼接所有条件），追加到 StringBuilder
     * <p>
     * 格式：条件1 [逻辑连接符] 条件2 [逻辑连接符] 条件3 ...
     * 示例：create_user_id = 1 AND create_time BETWEEN ... OR status = 1
     * </p>
     *
     * @param sqlBuilder        StringBuilder 对象
     * @param conditionContexts 条件上下文列表
     * @param userId            用户 ID
     */
    private void buildSimpleConditions(StringBuilder sqlBuilder, List<DataScopeConditionContext> conditionContexts, Long userId) {
        for (int i = 0; i < conditionContexts.size(); i++) {
            DataScopeConditionContext context = conditionContexts.get(i);

            // 添加逻辑连接符（第一个条件不需要）
            if (i > 0) {
                sqlBuilder.append(StringPools.SPACE)
                        .append(context.getLogic())
                        .append(StringPools.SPACE);
            }

            // 构建单个条件
            buildSingleCondition(sqlBuilder, context, userId);
        }
    }

    // ================================ 默认权限创建方法 ================================

    /**
     * 构建用于正常路径的全量访问数据权限（{@linkplain DataScopeTypeEnum#ALL}）。
     * <p>
     * 例如在角色未配置或用户无匹配权限时，默认将其映射为全局可见的权限范围。
     * </p>
     *
     * @param permissionCode 权限标识
     * @return {@link DataScope } 全量访问权限对象
     * @author payne.zhuang
     * @CreateTime 2025-12-19 - 13:35
     */
    private DataScope createFullAccessDataScope(String permissionCode) {
        return createFullAccessDataScope(getCurrentUserId(), permissionCode);
    }

    /**
     * 构建用于正常路径的全量访问数据权限（{@linkplain DataScopeTypeEnum#ALL}），可以显式指定当前用户 ID。
     *
     * @param userId         用户 ID，可为空
     * @param permissionCode 权限标识
     * @return {@link DataScope } 全量访问权限对象
     * @author payne.zhuang
     * @CreateTime 2025-12-19 - 13:35
     */
    private DataScope createFullAccessDataScope(Long userId, String permissionCode) {
        Long currentUserId = null != userId ? userId : GlobalUserHolder.getUserId();
        return DataScope.builder()
                .scopeType(DataScopeTypeEnum.ALL)
                .currentUserId(currentUserId)
                .scopeUserIds(Collections.emptySet())
                .permissionCode(permissionCode)
                .build();
    }

    /**
     * 构建异常降级的安全拒绝权限（{@linkplain DataScopeTypeEnum#UN_KNOWN}），用于服务不可用或计算出错时。
     *
     * @param permissionCode 权限标识
     * @return {@link DataScope } 安全降级权限对象
     * @author payne.zhuang
     * @CreateTime 2025-12-19 - 13:35
     */
    private DataScope createSafeUnknownDataScope(String permissionCode) {
        return createSafeUnknownDataScope(getCurrentUserId(), permissionCode);
    }

    /**
     * 构建异常降级的安全拒绝权限（{@linkplain DataScopeTypeEnum#UN_KNOWN}），可指定当前用户 ID。
     * <p>
     * 在权限计算抛出异常或服务不可用的场景下返回，确保不会暴露任何数据。
     * </p>
     *
     * @param userId         用户 ID，可为空
     * @param permissionCode 权限标识，可为空
     * @return 异常降级的 {@link DataScope} 对象
     * @author payne.zhuang
     * @CreateTime 2025-12-19 - 13:35
     */
    private DataScope createSafeUnknownDataScope(Long userId, String permissionCode) {
        Long currentUserId = null != userId ? userId : GlobalUserHolder.getUserId();
        return DataScope.builder()
                .scopeType(DataScopeTypeEnum.UN_KNOWN)
                .currentUserId(currentUserId)
                .scopeUserIds(null != currentUserId ? Set.of(currentUserId) : Collections.emptySet())
                .permissionCode(permissionCode)
                .build();
    }
}
