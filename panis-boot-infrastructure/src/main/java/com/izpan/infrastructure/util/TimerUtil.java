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

package com.izpan.infrastructure.util;

import lombok.experimental.UtilityClass;

/**
 * 计时器工具类
 * <p>
 * 用于自动计算耗时，避免重复编写耗时统计代码
 * </p>
 * <p>
 * 使用示例：
 * <pre>
 * TimerUtil.Timer timer = TimerUtil.start();
 * // 执行需要计时的操作
 * long duration = timer.duration(); // 获取耗时（毫秒）
 * </pre>
 * </p>
 *
 * @Author payne.zhuang <paynezhuang@gmail.com>
 * @ProjectName panis-boot
 * @ClassName com.izpan.infrastructure.util.TimerUtil
 * @CreateTime 2025-12-23 22:22:36
 */
@UtilityClass
public class TimerUtil {

    /**
     * 计时器内部类
     */
    public static class Timer {
        private final long startTime;

        private Timer() {
            this.startTime = System.nanoTime();
        }

        /**
         * 获取耗时（毫秒）
         *
         * @return 耗时（毫秒）
         */
        public long duration() {
            return (System.nanoTime() - startTime) / 1_000_000;
        }
    }

    /**
     * 创建计时器
     *
     * @return 计时器实例
     */
    public static Timer start() {
        return new Timer();
    }
}

