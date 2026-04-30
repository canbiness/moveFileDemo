package com.example.filetransfer.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * H2 兼容性修复启动器。
 * 用于修复旧版 H2 文件库里枚举列类型不一致导致的兼容问题。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class H2SchemaCompatibilityRunner implements ApplicationRunner {

    /** 用于执行兼容性 SQL。 */
    private final JdbcTemplate jdbcTemplate;

    /** 用于读取当前数据源配置。 */
    private final Environment environment;

    /**
     * 应用启动后执行一次 H2 兼容性修复。
     *
     * @param args 启动参数
     */
    @Override
    public void run(ApplicationArguments args) {
        String url = environment.getProperty("spring.datasource.url", "");
        // 只有 H2 数据库才需要做这组兼容处理，其他数据库直接跳过。
        if (!url.startsWith("jdbc:h2:")) {
            return;
        }

        log.info("Checking H2 schema compatibility for enum-backed columns");
        List<String> statements = List.of(
                "ALTER TABLE IF EXISTS transfer_tasks ALTER COLUMN status VARCHAR(32)",
                "ALTER TABLE IF EXISTS transfer_tasks ALTER COLUMN transfer_type VARCHAR(32)",
                "ALTER TABLE IF EXISTS transfer_tasks ALTER COLUMN verification_mode VARCHAR(32)",
                "ALTER TABLE IF EXISTS transfer_batches ALTER COLUMN status VARCHAR(32)",
                "ALTER TABLE IF EXISTS transfer_batches ALTER COLUMN temperature_tier VARCHAR(32)",
                "ALTER TABLE IF EXISTS scalable_file_records ALTER COLUMN status VARCHAR(32)"
        );

        for (String statement : statements) {
            try {
                // 逐条执行，保证单条失败不会影响其他列的修复。
                jdbcTemplate.execute(statement);
                log.debug("Applied compatibility statement: {}", statement);
            } catch (Exception ex) {
                // 这里允许“尽力而为”，例如列已经是正确类型时会自然跳过。
                log.debug("Skipped compatibility statement: {}, reason={}", statement, ex.getMessage());
            }
        }
    }
}
