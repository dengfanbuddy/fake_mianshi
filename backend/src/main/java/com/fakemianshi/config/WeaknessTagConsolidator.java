package com.fakemianshi.config;

import com.fakemianshi.service.WeaknessTagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 启动时把历史弱点标签归并一次。
 *
 * <p>为什么放在启动而不是只留个接口：老库里的同义写法是既成事实，
 * 光修写入路径不会让已经存在的 69 条变干净，用户得自己找按钮。
 * 归并是幂等的（没有可合并的就什么都不做），且原始写法全部保留在 aliases 里，
 * 不是不可逆的数据改写。
 *
 * <p>{@code @Order} 放到最后：等 schema 迁移补上 canonical_point 列之后再跑。
 */
@Slf4j
@Component
@Order(100)
@RequiredArgsConstructor
public class WeaknessTagConsolidator implements CommandLineRunner {

    private final WeaknessTagService weaknessTagService;

    @Override
    public void run(String... args) {
        try {
            weaknessTagService.consolidateAll();
        } catch (Exception e) {
            // 启动期任何异常都不该挡住应用起来
            log.warn("启动时归并弱点标签失败（忽略）: {}", e.getMessage());
        }
    }
}
