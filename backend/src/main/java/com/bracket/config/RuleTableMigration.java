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
        ruleColumns.put("required_lubrication_points", "INT DEFAULT NULL COMMENT '当班润滑要求点数，空表示未维护'");
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
            if (!tableExists(metaData, "first_article_inspection")) {
                try (Statement statement = connection.createStatement()) {
                    statement.execute("CREATE TABLE first_article_inspection ("
                            + "id BIGINT PRIMARY KEY AUTO_INCREMENT,"
                            + "form_no VARCHAR(50) DEFAULT NULL COMMENT '确认单号，开单后系统生成',"
                            + "equipment_id BIGINT NOT NULL COMMENT '封口设备ID',"
                            + "mold_batch_record_id BIGINT DEFAULT NULL COMMENT '开单时当前换模批次记录ID',"
                            + "batch_no VARCHAR(100) NOT NULL COMMENT '模具批次号快照',"
                            + "mold_model VARCHAR(100) NOT NULL COMMENT '模具型号快照',"
                            + "standard_length DECIMAL(10,2) NOT NULL COMMENT '标准长(mm)',"
                            + "standard_width DECIMAL(10,2) NOT NULL COMMENT '标准宽(mm)',"
                            + "standard_height DECIMAL(10,2) NOT NULL COMMENT '标准高(mm)',"
                            + "tolerance_mm DECIMAL(10,2) NOT NULL COMMENT '公差(±mm)',"
                            + "measured_length DECIMAL(10,2) NOT NULL COMMENT '实测长(mm)',"
                            + "measured_width DECIMAL(10,2) NOT NULL COMMENT '实测宽(mm)',"
                            + "measured_height DECIMAL(10,2) NOT NULL COMMENT '实测高(mm)',"
                            + "length_deviation DECIMAL(10,2) NOT NULL COMMENT '长量差=实测-标准(mm)',"
                            + "width_deviation DECIMAL(10,2) NOT NULL COMMENT '宽量差=实测-标准(mm)',"
                            + "height_deviation DECIMAL(10,2) NOT NULL COMMENT '高量差=实测-标准(mm)',"
                            + "out_of_tolerance TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否超线：1超线禁止放行',"
                            + "status VARCHAR(20) NOT NULL COMMENT 'PENDING待签放/RELEASED已放行/RETURNED已退回再量',"
                            + "operator VARCHAR(100) NOT NULL COMMENT '开单人（调度）',"
                            + "remark VARCHAR(500) DEFAULT NULL COMMENT '备注',"
                            + "release_signer VARCHAR(100) DEFAULT NULL COMMENT '签放人',"
                            + "release_time DATETIME DEFAULT NULL COMMENT '签放时间',"
                            + "return_operator VARCHAR(100) DEFAULT NULL COMMENT '退回人',"
                            + "return_reason VARCHAR(500) DEFAULT NULL COMMENT '退回原因',"
                            + "return_time DATETIME DEFAULT NULL COMMENT '退回时间',"
                            + "create_time DATETIME DEFAULT CURRENT_TIMESTAMP,"
                            + "UNIQUE KEY uk_fai_form_no (form_no),"
                            + "INDEX idx_fai_equipment (equipment_id),"
                            + "INDEX idx_fai_status (status),"
                            + "INDEX idx_fai_create_time (create_time),"
                            + "CONSTRAINT fk_fai_equipment FOREIGN KEY (equipment_id) "
                            + "REFERENCES equipment(id) ON DELETE CASCADE,"
                            + "CONSTRAINT fk_fai_mold_batch FOREIGN KEY (mold_batch_record_id) "
                            + "REFERENCES mold_batch_record(id) ON DELETE SET NULL"
                            + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='首件尺寸确认单表'");
                    log.info("配套规则迁移：新建 first_article_inspection 首件尺寸确认单表");
                }
            }
            if (!tableExists(metaData, "bracket_repair_record")) {
                try (Statement statement = connection.createStatement()) {
                    statement.execute("CREATE TABLE bracket_repair_record ("
                            + "id BIGINT PRIMARY KEY AUTO_INCREMENT,"
                            + "bracket_id BIGINT NOT NULL COMMENT '支架ID',"
                            + "repair_no VARCHAR(100) NOT NULL COMMENT '返修单号',"
                            + "repair_reason VARCHAR(500) DEFAULT NULL COMMENT '送修原因',"
                            + "repair_time DATETIME NOT NULL COMMENT '送修时间',"
                            + "repair_operator VARCHAR(100) DEFAULT NULL COMMENT '送修人',"
                            + "return_result TINYINT(1) DEFAULT NULL COMMENT '回库结论：1合格/0不合格，未回库为空',"
                            + "return_time DATETIME DEFAULT NULL COMMENT '回库时间',"
                            + "inspector VARCHAR(100) DEFAULT NULL COMMENT '回库检验人',"
                            + "return_remark VARCHAR(500) DEFAULT NULL COMMENT '回库备注',"
                            + "create_time DATETIME DEFAULT CURRENT_TIMESTAMP,"
                            + "INDEX idx_repair_bracket (bracket_id),"
                            + "INDEX idx_repair_return_time (return_time),"
                            + "CONSTRAINT fk_repair_bracket FOREIGN KEY (bracket_id) "
                            + "REFERENCES bracket(id) ON DELETE CASCADE"
                            + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支架返修单记录表'");
                    log.info("配套规则迁移：新建 bracket_repair_record 支架返修单记录表");
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
