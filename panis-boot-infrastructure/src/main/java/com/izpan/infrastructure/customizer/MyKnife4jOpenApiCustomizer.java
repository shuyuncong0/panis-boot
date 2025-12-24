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

package com.izpan.infrastructure.customizer;

import com.github.xiaoymin.knife4j.annotations.ApiSupport;
import com.github.xiaoymin.knife4j.core.conf.ExtensionsConstants;
import com.github.xiaoymin.knife4j.core.conf.GlobalConstants;
import com.github.xiaoymin.knife4j.spring.configuration.Knife4jProperties;
import com.github.xiaoymin.knife4j.spring.configuration.Knife4jSetting;
import com.github.xiaoymin.knife4j.spring.extension.Knife4jOpenApiCustomizer;
import com.github.xiaoymin.knife4j.spring.extension.OpenApiExtensionResolver;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.models.OpenAPI;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Knife4j OpenApi 自定义增强扩展支持，复制于 {@link com.github.xiaoymin.knife4j.spring.extension.Knife4jOpenApiCustomizer} <p>
 * 解决 SpringBoot 3.4.+ 下无法启用增强功能的问题
 *
 * @Author payne.zhuang <paynezhuang@gmail.com>
 * @ProjectName panis-boot
 * @ClassName com.izpan.infrastructure.customizer.MyKnife4jOpenApiCustomizer
 * @CreateTime 2025/8/14 - 10:49
 */

@Slf4j
@SuppressWarnings("all")
public class MyKnife4jOpenApiCustomizer extends Knife4jOpenApiCustomizer {

    private final Knife4jProperties knife4jProperties;
    private final SpringDocConfigProperties properties;

    public MyKnife4jOpenApiCustomizer(Knife4jProperties knife4jProperties, SpringDocConfigProperties properties) {
        super(knife4jProperties, properties);
        this.knife4jProperties = knife4jProperties;
        this.properties = properties;
    }

    @Override
    public void customise(OpenAPI openApi) {
        log.debug("Knife4j OpenApiCustomizer");
        if (knife4jProperties.isEnable()) {
            Knife4jSetting setting = knife4jProperties.getSetting();
            OpenApiExtensionResolver openApiExtensionResolver = new OpenApiExtensionResolver(setting, knife4jProperties.getDocuments());
            // 解析初始化
            openApiExtensionResolver.start();
            Map<String, Object> objectMap = new HashMap<>();
            objectMap.put(GlobalConstants.EXTENSION_OPEN_SETTING_NAME, setting);
            objectMap.put(GlobalConstants.EXTENSION_OPEN_MARKDOWN_NAME, openApiExtensionResolver.getMarkdownFiles());
            openApi.addExtension(GlobalConstants.EXTENSION_OPEN_API_NAME, objectMap);
            addOrderExtension(openApi);
        }
    }

    /**
     * 往OpenAPI内tags字段添加x-order属性
     *
     * @param openApi openApi
     */
    private void addOrderExtension(OpenAPI openApi) {
        Set<SpringDocConfigProperties.GroupConfig> configs = properties.getGroupConfigs();
        if (CollectionUtils.isEmpty(configs)) {
            return;
        }
        // 获取包扫描路径
        Set<String> packagesToScan =
                configs.stream()
                        .map(SpringDocConfigProperties.GroupConfig::getPackagesToScan)
                        .filter(toScan -> !CollectionUtils.isEmpty(toScan))
                        .flatMap(List::stream)
                        .collect(Collectors.toSet());
        if (CollectionUtils.isEmpty(packagesToScan)) {
            return;
        }
        // 扫描包下被ApiSupport注解的RestController Class
        Set<Class<?>> classes =
                packagesToScan.stream()
                        .map(this::scanPackageByAnnotation)
                        .flatMap(Set::stream)
                        .filter(clazz -> clazz.isAnnotationPresent(ApiSupport.class))
                        .collect(Collectors.toSet());
        if (!CollectionUtils.isEmpty(classes)) {
            // ApiSupport order 值存入 tagSortMap<Tag.name,ApiSupport.order>
            Map<String, Integer> tagOrderMap = new HashMap<>();
            classes.forEach(
                    clazz -> {
                        Tag tag = getTag(clazz);
                        if (Objects.nonNull(tag)) {
                            ApiSupport apiSupport = clazz.getAnnotation(ApiSupport.class);
                            tagOrderMap.putIfAbsent(tag.name(), apiSupport.order());
                        }
                    });
            // 往openApi tags字段添加x-order增强属性
            if (openApi.getTags() != null) {
                openApi
                        .getTags()
                        .forEach(
                                tag -> {
                                    if (tagOrderMap.containsKey(tag.getName())) {
                                        tag.addExtension(
                                                ExtensionsConstants.EXTENSION_ORDER, tagOrderMap.get(tag.getName()));
                                    }
                                });
            }
        }
    }

    private Tag getTag(Class<?> clazz) {
        // 从类上获取
        Tag tag = clazz.getAnnotation(Tag.class);
        if (Objects.isNull(tag)) {
            // 从接口上获取
            Class<?>[] interfaces = clazz.getInterfaces();
            if (ArrayUtils.isNotEmpty(interfaces)) {
                for (Class<?> interfaceClazz : interfaces) {
                    Tag anno = interfaceClazz.getAnnotation(Tag.class);
                    if (Objects.nonNull(anno)) {
                        tag = anno;
                        break;
                    }
                }
            }
        }
        return tag;
    }

    private Set<Class<?>> scanPackageByAnnotation(String packageName) {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        Set<Class<?>> classes = new HashSet<>();
        for (BeanDefinition beanDefinition : scanner.findCandidateComponents(packageName)) {
            try {
                Class<?> clazz = Class.forName(beanDefinition.getBeanClassName());
                classes.add(clazz);
            } catch (ClassNotFoundException ignore) {

            }
        }
        return classes;
    }
}
