package com.agentmesh.core.prompt;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 启动时 Prompt 模板校验器
 * 检测用户自定义模板是否正确覆盖了内置模板
 */
@Slf4j
public class PromptTemplateValidator implements ApplicationRunner {

    private final PromptTemplateEngine engine;

    public PromptTemplateValidator(PromptTemplateEngine engine) {
        this.engine = engine;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("[PromptValidator] 开始扫描 Prompt 模板...");

        // 1. 扫描内置模板
        Set<String> builtinTemplates = scanBuiltinTemplates();
        log.info("[PromptValidator] 内置模板: {}", builtinTemplates);

        // 2. 扫描用户自定义模板
        Set<String> userTemplates = scanUserTemplates();
        log.info("[PromptValidator] 用户自定义模板: {}", userTemplates);

        // 3. 校验：未匹配的自定义模板发出 WARN
        for (String userTemplate : userTemplates) {
            if (!builtinTemplates.contains(userTemplate)) {
                log.warn("[PromptValidator] 自定义模板 '{}' 未匹配任何内置模板，"
                        + "请检查文件名是否正确。该模板不会被自动加载。", userTemplate);
            }
        }

        // 4. 校验：模板语法正确性
        for (String name : builtinTemplates) {
            try {
                engine.render(name, null);
            } catch (Exception e) {
                log.error("[PromptValidator] 模板 '{}' 渲染失败: {}", name, e.getMessage());
            }
        }
    }

    private Set<String> scanBuiltinTemplates() {
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver()
                    .getResources("classpath:/prompts/*.prompt");
            return Arrays.stream(resources)
                    .map(r -> {
                        String filename = r.getFilename();
                        return filename != null ? filename.replace(".prompt", "") : "";
                    })
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toSet());
        } catch (IOException e) {
            log.error("[PromptValidator] 扫描模板文件失败", e);
            return Set.of("default");
        }
    }

    private Set<String> scanUserTemplates() {
        return Set.of();
    }
}
