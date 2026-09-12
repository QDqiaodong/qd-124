CREATE TABLE IF NOT EXISTS equipment (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    equipment_code VARCHAR(50) NOT NULL UNIQUE,
    equipment_name VARCHAR(100),
    max_brackets INT DEFAULT NULL,
    allowed_models VARCHAR(1000) DEFAULT NULL,
    allowed_mold_models VARCHAR(1000) DEFAULT NULL,
    min_length DECIMAL(10,2) DEFAULT NULL,
    max_length DECIMAL(10,2) DEFAULT NULL,
    min_width DECIMAL(10,2) DEFAULT NULL,
    max_width DECIMAL(10,2) DEFAULT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS bracket (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    model VARCHAR(100) NOT NULL,
    length_mm DECIMAL(10,2) NOT NULL,
    width_mm DECIMAL(10,2) NOT NULL,
    equipment_id BIGINT DEFAULT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS mold_batch_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    equipment_id BIGINT NOT NULL,
    batch_no VARCHAR(100) NOT NULL,
    mold_model VARCHAR(100) NOT NULL,
    change_time TIMESTAMP NOT NULL,
    operator VARCHAR(100) DEFAULT NULL,
    remark VARCHAR(500) DEFAULT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS bracket_repair_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    bracket_id BIGINT NOT NULL,
    repair_no VARCHAR(100) NOT NULL,
    repair_reason VARCHAR(500) DEFAULT NULL,
    repair_time TIMESTAMP NOT NULL,
    repair_operator VARCHAR(100) DEFAULT NULL,
    return_result BOOLEAN DEFAULT NULL,
    return_time TIMESTAMP DEFAULT NULL,
    inspector VARCHAR(100) DEFAULT NULL,
    return_remark VARCHAR(500) DEFAULT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_mold_batch_equipment ON mold_batch_record (equipment_id);
CREATE INDEX IF NOT EXISTS idx_mold_batch_change_time ON mold_batch_record (change_time);
CREATE INDEX IF NOT EXISTS idx_repair_bracket ON bracket_repair_record (bracket_id);
CREATE INDEX IF NOT EXISTS idx_repair_return_time ON bracket_repair_record (return_time);
