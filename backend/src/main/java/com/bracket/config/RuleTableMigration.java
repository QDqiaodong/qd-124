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
 * 设备配套规则字段与换模批次表的幂等迁移。
 * 全新环境由 mysql/schema.sql 建表；已有数据卷启动时自动补齐规则列与换模批次表。
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
        ruleColumns.put("allowed_mold_models", "VARCHAR(1000) DEFAULT NULL COMMENT '允许模具型号清单，逗号分隔'");
        ruleColumns.put("min_length", "DECIMAL(10,2) DEFAULT NULL COMMENT '允许最小长度(mm)'");
        ruleColumns.put("max_length", "DECIMAL(10,2) DEFAULT NULL COMMENT '允许最大长度(mm)'");
        ruleColumns.put("min_width", "DECIMAL(10,2) DEFAULT NULL COMMENT '允许最小宽度(mm)'");
        ruleColumns.put("max_width", "DECIMAL(10,2) DEFAULT NULL COMMENT '允许最大宽度(mm)'");

        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            for (Map.Entry<String, String> entry : ruleColumns.entrySet()) {
                if (!columnExists(metaData, "equipment", entry.getKey())) {
                    try (Statement statement = connection.createStatement()) {
                        statement.execute("ALTER TABLE equipment ADD COLUMN " + entry.getKey() + " " + entry.getValue());
                        log.info("配套规则迁移：equipment 表新增列 {}", entry.getKey());
                    }
                }
            }
            if (!tableExists(metaData, "mold_batch_record")) {
                try (Statement statement = connection.createStatement()) {
                    statement.execute("CREATE TABLE mold_batch_record ("
                            + "id BIGINT PRIMARY KEY AUTO_INCREMENT,"
                            + "equipment_id BIGINT NOT NULL COMMENT '封口设备ID',"
                            + "batch_no VARCHAR(100) NOT NULL COMMENT '模具批次号',"
                            + "mold_model VARCHAR(100) NOT NULL COMMENT '模具型号',"
                            + "change_time DATETIME NOT NULL COMMENT '换模时间',"
                            + "operator VARCHAR(100) DEFAULT NULL COMMENT '换模操作人',"
                            + "remark VARCHAR(500) DEFAULT NULL COMMENT '备注',"
                            + "create_time DATETIME DEFAULT CURRENT_TIMESTAMP,"
                            + "INDEX idx_mold_batch_equipment (equipment_id),"
                            + "INDEX idx_mold_batch_change_time (change_time),"
                            + "CONSTRAINT fk_mold_batch_equipment FOREIGN KEY (equipment_id) "
                            + "REFERENCES equipment(id) ON DELETE CASCADE"
                            + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='换模批次记录表'");
                    log.info("配套规则迁移：新建 mold_batch_record 换模批次记录表");
                }
            }
        } catch (Exception e) {
            log.error("配套规则字段迁移失败", e);
            throw new IllegalStateException("配套规则字段迁移失败: " + e.getMessage(), e);
        }
    }

    private boolean columnExists(DatabaseMetaData metaData, String tableName, String columnName) throws Exception {
        try (ResultSet columns = metaData.getColumns(null, null, tableName, columnName)) {
            return columns.next();
        }
    }

    private boolean tableExists(DatabaseMetaData metaData, String tableName) throws Exception {
        try (ResultSet tables = metaData.getTables(null, null, tableName, new String[]{"TABLE"})) {
            return tables.next();
        }
    }
}
