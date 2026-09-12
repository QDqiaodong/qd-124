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
