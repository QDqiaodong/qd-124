package com.bracket.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 设备配套规则字段的幂等迁移。
 * 全新环境由 mysql/schema.sql 建表；已有数据卷启动时自动补齐规则列。
 */
@Component
public class RuleTableMigration implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(RuleTableMigration.class);

    private final DataSource dataSource;

    public RuleTableMigration(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void afterPropertiesSet() {
        Map<String, String> ruleColumns = new LinkedHashMap<>();
        ruleColumns.put("max_brackets", "INT DEFAULT NULL COMMENT '最大支架数量，空表示不限制'");
        ruleColumns.put("allowed_models", "VARCHAR(1000) DEFAULT NULL COMMENT '允许型号，逗号分隔，空表示不限制'");
        ruleColumns.put("min_length", "DECIMAL(10,2) DEFAULT NULL COMMENT '允许最小长度(mm)'");
        ruleColumns.put("max_length", "DECIMAL(10,2) DEFAULT NULL COMMENT '允许最大长度(mm)'");
        ruleColumns.put("min_width", "DECIMAL(10,2) DEFAULT NULL COMMENT '允许最小宽度(mm)'");
        ruleColumns.put("max_width", "DECIMAL(10,2) DEFAULT NULL COMMENT '允许最大宽度(mm)'");

        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            for (Map.Entry<String, String> entry : ruleColumns.entrySet()) {
                if (!columnExists(metaData, entry.getKey())) {
                    try (Statement statement = connection.createStatement()) {
                        statement.execute("ALTER TABLE equipment ADD COLUMN " + entry.getKey() + " " + entry.getValue());
                        log.info("配套规则迁移：equipment 表新增列 {}", entry.getKey());
                    }
                }
            }
        } catch (Exception e) {
            log.error("配套规则字段迁移失败", e);
            throw new IllegalStateException("配套规则字段迁移失败: " + e.getMessage(), e);
        }
    }

    private boolean columnExists(DatabaseMetaData metaData, String columnName) throws Exception {
        try (ResultSet columns = metaData.getColumns(null, null, "equipment", columnName)) {
            return columns.next();
        }
    }
}
