CREATE TABLE IF NOT EXISTS equipment (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    equipment_code VARCHAR(50) NOT NULL UNIQUE COMMENT '封口机设备编号',
    equipment_name VARCHAR(100) COMMENT '设备名称',
    max_brackets INT DEFAULT NULL COMMENT '最大支架数量，空表示不限制',
    allowed_models VARCHAR(1000) DEFAULT NULL COMMENT '允许型号，逗号分隔，空表示不限制',
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

INSERT INTO equipment (equipment_code, equipment_name, max_brackets, allowed_models, min_length, max_length, min_width, max_width) VALUES
('FK-001', '1号封口机', 3, 'ST-A001,ST-B002', 250.00, 450.00, 120.00, 220.00),
('FK-002', '2号封口机', 2, 'ST-C003,ST-E005', 200.00, 350.00, 100.00, 180.00),
('FK-003', '3号封口机', 4, NULL, 300.00, 500.00, NULL, NULL),
('FK-004', '4号封口机', 2, 'ST-D004,ST-G007', NULL, NULL, NULL, NULL),
('FK-005', '5号封口机', NULL, NULL, NULL, NULL, NULL, NULL);

INSERT INTO bracket (name, model, length_mm, width_mm, equipment_id) VALUES
('A型支架', 'ST-A001', 300.00, 150.00, 1),
('B型支架', 'ST-B002', 400.00, 200.00, 1),
('C型支架', 'ST-C003', 250.00, 120.00, 2),
('D型支架', 'ST-D004', 350.00, 180.00, NULL),
('E型支架', 'ST-E005', 280.00, 140.00, NULL),
('F型支架', 'ST-F006', 450.00, 220.00, 3),
('G型支架', 'ST-G007', 320.00, 160.00, NULL),
('H型支架', 'ST-H008', 380.00, 190.00, NULL);
