CREATE TABLE IF NOT EXISTS equipment (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    equipment_code VARCHAR(50) NOT NULL UNIQUE COMMENT '封口机设备编号',
    equipment_name VARCHAR(100) COMMENT '设备名称',
    max_brackets INT DEFAULT NULL COMMENT '最大支架数量，空表示不限制',
    allowed_models VARCHAR(1000) DEFAULT NULL COMMENT '允许支架型号，逗号分隔，空表示不限制',
    allowed_mold_models VARCHAR(1000) DEFAULT NULL COMMENT '允许模具型号清单，逗号分隔；换模批次型号必须在此清单内',
    min_length DECIMAL(10,2) DEFAULT NULL COMMENT '允许最小长度(mm)',
    max_length DECIMAL(10,2) DEFAULT NULL COMMENT '允许最大长度(mm)',
    min_width DECIMAL(10,2) DEFAULT NULL COMMENT '允许最小宽度(mm)',
    max_width DECIMAL(10,2) DEFAULT NULL COMMENT '允许最大宽度(mm)',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_equipment_code (equipment_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='封口设备表';

CREATE TABLE IF NOT EXISTS bracket (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL COMMENT '支架名称',
    model VARCHAR(100) NOT NULL COMMENT '支架型号',
    length_mm DECIMAL(10,2) NOT NULL COMMENT '长度(mm)',
    width_mm DECIMAL(10,2) NOT NULL COMMENT '宽度(mm)',
    equipment_id BIGINT DEFAULT NULL COMMENT '绑定的封口设备ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_model (model),
    INDEX idx_equipment_id (equipment_id),
    CONSTRAINT fk_bracket_equipment FOREIGN KEY (equipment_id) REFERENCES equipment(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支架档案表';

CREATE TABLE IF NOT EXISTS mold_batch_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    equipment_id BIGINT NOT NULL COMMENT '封口设备ID',
    batch_no VARCHAR(100) NOT NULL COMMENT '模具批次号',
    mold_model VARCHAR(100) NOT NULL COMMENT '模具型号',
    change_time DATETIME NOT NULL COMMENT '换模时间',
    operator VARCHAR(100) DEFAULT NULL COMMENT '换模操作人',
    remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_mold_batch_equipment (equipment_id),
    INDEX idx_mold_batch_change_time (change_time),
    CONSTRAINT fk_mold_batch_equipment FOREIGN KEY (equipment_id) REFERENCES equipment(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='换模批次记录表（最新一条为当前批次，旧批次仅作历史追溯）';

CREATE TABLE IF NOT EXISTS first_article_inspection (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    form_no VARCHAR(50) DEFAULT NULL COMMENT '确认单号，开单后系统生成（FA-日期-序号）',
    equipment_id BIGINT NOT NULL COMMENT '封口设备ID',
    mold_batch_record_id BIGINT DEFAULT NULL COMMENT '开单时当前换模批次记录ID（显示以快照为准）',
    batch_no VARCHAR(100) NOT NULL COMMENT '模具批次号快照',
    mold_model VARCHAR(100) NOT NULL COMMENT '模具型号快照',
    standard_length DECIMAL(10,2) NOT NULL COMMENT '标准长(mm)',
    standard_width DECIMAL(10,2) NOT NULL COMMENT '标准宽(mm)',
    standard_height DECIMAL(10,2) NOT NULL COMMENT '标准高(mm)',
    tolerance_mm DECIMAL(10,2) NOT NULL COMMENT '公差(±mm)，长宽高共用',
    measured_length DECIMAL(10,2) NOT NULL COMMENT '实测长(mm)',
    measured_width DECIMAL(10,2) NOT NULL COMMENT '实测宽(mm)',
    measured_height DECIMAL(10,2) NOT NULL COMMENT '实测高(mm)',
    length_deviation DECIMAL(10,2) NOT NULL COMMENT '长量差=实测-标准(mm)',
    width_deviation DECIMAL(10,2) NOT NULL COMMENT '宽量差=实测-标准(mm)',
    height_deviation DECIMAL(10,2) NOT NULL COMMENT '高量差=实测-标准(mm)',
    out_of_tolerance TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否超线：1超线，禁止放行只能退回再量',
    status VARCHAR(20) NOT NULL COMMENT '放行结果：PENDING待签放/RELEASED已放行/RETURNED已退回再量',
    operator VARCHAR(100) NOT NULL COMMENT '开单人（调度）',
    remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
    release_signer VARCHAR(100) DEFAULT NULL COMMENT '签放人',
    release_time DATETIME DEFAULT NULL COMMENT '签放时间',
    return_operator VARCHAR(100) DEFAULT NULL COMMENT '退回人',
    return_reason VARCHAR(500) DEFAULT NULL COMMENT '退回原因',
    return_time DATETIME DEFAULT NULL COMMENT '退回时间',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_fai_form_no (form_no),
    INDEX idx_fai_equipment (equipment_id),
    INDEX idx_fai_status (status),
    INDEX idx_fai_create_time (create_time),
    CONSTRAINT fk_fai_equipment FOREIGN KEY (equipment_id) REFERENCES equipment(id) ON DELETE CASCADE,
    CONSTRAINT fk_fai_mold_batch FOREIGN KEY (mold_batch_record_id) REFERENCES mold_batch_record(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='首件尺寸确认单表（超线单禁止放行，签字留痕可追溯）';

CREATE TABLE IF NOT EXISTS bracket_repair_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    bracket_id BIGINT NOT NULL COMMENT '支架ID',
    repair_no VARCHAR(100) NOT NULL COMMENT '返修单号',
    repair_reason VARCHAR(500) DEFAULT NULL COMMENT '送修原因/故障描述',
    repair_time DATETIME NOT NULL COMMENT '送修时间',
    repair_operator VARCHAR(100) DEFAULT NULL COMMENT '送修人',
    return_result TINYINT(1) DEFAULT NULL COMMENT '回库结论：1合格/0不合格，未回库为空',
    return_time DATETIME DEFAULT NULL COMMENT '回库时间',
    inspector VARCHAR(100) DEFAULT NULL COMMENT '回库检验人',
    return_remark VARCHAR(500) DEFAULT NULL COMMENT '回库备注',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_repair_bracket (bracket_id),
    INDEX idx_repair_return_time (return_time),
    CONSTRAINT fk_repair_bracket FOREIGN KEY (bracket_id) REFERENCES bracket(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支架返修单表（最新一张为当前返修状态，旧单仅作历史追溯）';

INSERT INTO equipment (equipment_code, equipment_name, max_brackets, allowed_models, allowed_mold_models, min_length, max_length, min_width, max_width) VALUES
('FK-001', '1号封口机', 3, 'ST-A001,ST-B002', 'MD-X10,MD-X20', 250.00, 450.00, 120.00, 220.00),
('FK-002', '2号封口机', 2, 'ST-C003,ST-E005', 'MD-Y10', 200.00, 350.00, 100.00, 180.00),
('FK-003', '3号封口机', 4, NULL, 'MD-Z30', 300.00, 500.00, NULL, NULL),
('FK-004', '4号封口机', 2, 'ST-D004,ST-G007', 'MD-X10,MD-W40', NULL, NULL, NULL, NULL),
('FK-005', '5号封口机', NULL, NULL, NULL, NULL, NULL, NULL, NULL);

INSERT INTO bracket (name, model, length_mm, width_mm, equipment_id) VALUES
('A型支架', 'ST-A001', 300.00, 150.00, 1),
('B型支架', 'ST-B002', 400.00, 200.00, 1),
('C型支架', 'ST-C003', 250.00, 120.00, 2),
('D型支架', 'ST-D004', 350.00, 180.00, NULL),
('E型支架', 'ST-E005', 280.00, 140.00, NULL),
('F型支架', 'ST-F006', 450.00, 220.00, 3),
('G型支架', 'ST-G007', 320.00, 160.00, NULL),
('H型支架', 'ST-H008', 380.00, 190.00, NULL);

-- FK-001：当前批次 MD-X10（在允许清单内）；保留一条更早的旧批次，仅作历史、不再放行
INSERT INTO mold_batch_record (equipment_id, batch_no, mold_model, change_time, operator, remark) VALUES
(1, 'MB-20260801-01', 'MD-X20', DATE_SUB(NOW(), INTERVAL 30 DAY), '张工', '上一生产批次（历史记录）'),
(1, 'MB-20260901-02', 'MD-X10', DATE_SUB(NOW(), INTERVAL 2 DAY), '张工', '当前在机批次');

-- FK-003：当前批次型号不在允许清单内（允许 MD-Z30），用于演示批次不合规拦截
INSERT INTO mold_batch_record (equipment_id, batch_no, mold_model, change_time, operator, remark) VALUES
(3, 'MB-20260905-01', 'MD-OLD9', DATE_SUB(NOW(), INTERVAL 1 DAY), '李工', '误用非允许型号模具');

-- 返修演示：D型支架（id=4，解绑态）已检验合格回库可再次挂接；E型支架（id=5，解绑态）返修中尚未写回库结论，批量挂接/改挂将被拦截
INSERT INTO bracket_repair_record (bracket_id, repair_no, repair_reason, repair_time, repair_operator, return_result, return_time, inspector, return_remark) VALUES
(4, 'RP-20260820-01', '支耳变形，校形返修', DATE_SUB(NOW(), INTERVAL 20 DAY), '王工', 1, DATE_SUB(NOW(), INTERVAL 18 DAY), '陈检', '校形后尺寸复检合格，准予回库'),
(5, 'RP-20260910-01', '表面裂纹待处理', DATE_SUB(NOW(), INTERVAL 2 DAY), '王工', NULL, NULL, NULL, NULL);

-- 首件尺寸确认演示（FK-001 当前批次 MB-20260901-02）：一张已放行、一张量差超线退回再量、一张待签放
INSERT INTO first_article_inspection
(form_no, equipment_id, mold_batch_record_id, batch_no, mold_model, standard_length, standard_width, standard_height, tolerance_mm, measured_length, measured_width, measured_height, length_deviation, width_deviation, height_deviation, out_of_tolerance, status, operator, remark, release_signer, release_time, return_operator, return_reason, return_time, create_time) VALUES
('FA-20260911-0001', 1, 2, 'MB-20260901-02', 'MD-X10', 300.00, 150.00, 80.00, 0.50, 300.12, 149.95, 80.08, 0.12, -0.05, 0.08, 0, 'RELEASED', '王调度', '首件合格，准予放量', '陈检', DATE_SUB(NOW(), INTERVAL 2 DAY), NULL, NULL, NULL, DATE_SUB(NOW(), INTERVAL 2 DAY)),
('FA-20260912-0002', 1, 2, 'MB-20260901-02', 'MD-X10', 300.00, 150.00, 80.00, 0.50, 300.74, 150.10, 79.90, 0.74, 0.10, -0.10, 1, 'RETURNED', '王调度', '复测长度仍超线，已退回调模', NULL, NULL, '李工', '长量差0.74mm超线，退回再量', DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY)),
('FA-20260913-0003', 1, 2, 'MB-20260901-02', 'MD-X10', 300.00, 150.00, 80.00, 0.50, 300.05, 150.02, 80.01, 0.05, 0.02, 0.01, 0, 'PENDING', '王调度', '待品质签放', NULL, NULL, NULL, NULL, NULL, NOW());
