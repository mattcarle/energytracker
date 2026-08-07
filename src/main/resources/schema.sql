CREATE TABLE IF NOT EXISTS METER_POINT (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    mpan VARCHAR(255) NOT NULL,
    is_export BOOLEAN NOT NULL DEFAULT 0,
    meter_type VARCHAR(10) NOT NULL CHECK(meter_type IN ('GAS', 'ELEC')),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (mpan)
);

CREATE TABLE IF NOT EXISTS METER (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    serial_number VARCHAR(255) NOT NULL,
    meter_point_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (meter_point_id) REFERENCES METER_POINT(id),
    UNIQUE (meter_point_id, serial_number)
);

CREATE TABLE IF NOT EXISTS AGREEMENT (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tariff_code VARCHAR(255) NOT NULL,
    valid_from TIMESTAMP NOT NULL,
    valid_to TIMESTAMP,
    meter_point_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (meter_point_id) REFERENCES METER_POINT(id),
    UNIQUE (meter_point_id, tariff_code, valid_from)
);

CREATE TABLE IF NOT EXISTS USAGE (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    interval_from TIMESTAMP NOT NULL,
    interval_to TIMESTAMP NOT NULL,
    consumption DECIMAL(10, 4) NOT NULL,
    mpan VARCHAR(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS STANDING_CHARGE (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    agreement_id BIGINT NOT NULL,
    value_exc_vat DECIMAL(10, 4) NOT NULL,
    value_inc_vat DECIMAL(10, 4) NOT NULL,
    valid_from TIMESTAMP NOT NULL,
    valid_to TIMESTAMP,
    payment_method VARCHAR(50) NULL CHECK(payment_method IN ('DIRECT_DEBIT', 'NON_DIRECT_DEBIT', 'NA')),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (agreement_id) REFERENCES AGREEMENT(id),
    UNIQUE (agreement_id, payment_method, valid_from)
);

CREATE TABLE IF NOT EXISTS UNIT_RATE (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    agreement_id BIGINT NOT NULL,
    value_exc_vat DECIMAL(10, 6) NOT NULL,
    value_inc_vat DECIMAL(10, 6) NOT NULL,
    valid_from TIMESTAMP NOT NULL,
    valid_to TIMESTAMP,
    payment_method VARCHAR(50) NULL CHECK(payment_method IN ('DIRECT_DEBIT', 'NON_DIRECT_DEBIT', 'NA')),
    rate_type VARCHAR(20) NOT NULL DEFAULT 'STANDARD' CHECK(rate_type IN ('STANDARD', 'DAY', 'NIGHT')),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (agreement_id) REFERENCES AGREEMENT(id),
    UNIQUE (agreement_id, valid_from, payment_method, rate_type)
);

CREATE TABLE IF NOT EXISTS STANDING_CHARGE_BY_DAY (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    agreement_id BIGINT NOT NULL,
    value_exc_vat DECIMAL(10, 4) NOT NULL,
    value_inc_vat DECIMAL(10, 4) NOT NULL,
    valid_from TIMESTAMP NOT NULL,
    valid_to TIMESTAMP,
    payment_method VARCHAR(50) NULL CHECK(payment_method IN ('DIRECT_DEBIT', 'NON_DIRECT_DEBIT', 'NA')),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (agreement_id) REFERENCES AGREEMENT(id),
    UNIQUE (agreement_id, payment_method, valid_from)
);

CREATE TABLE IF NOT EXISTS UNIT_RATE_BY_HALF_HOUR (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    agreement_id BIGINT NOT NULL,
    value_exc_vat DECIMAL(10, 6) NOT NULL,
    value_inc_vat DECIMAL(10, 6) NOT NULL,
    valid_from TIMESTAMP NOT NULL,
    valid_to TIMESTAMP,
    payment_method VARCHAR(50) NULL CHECK(payment_method IN ('DIRECT_DEBIT', 'NON_DIRECT_DEBIT', 'NA')),
    rate_type VARCHAR(20) NOT NULL DEFAULT 'STANDARD' CHECK(rate_type IN ('STANDARD', 'DAY', 'NIGHT')),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (agreement_id) REFERENCES AGREEMENT(id),
    UNIQUE (agreement_id, valid_from, payment_method, rate_type)
);

CREATE TABLE IF NOT EXISTS UTC_TO_LOCAL (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    utc_time TIMESTAMP NOT NULL,
    local_time TIMESTAMP NOT NULL,
    time_zone VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS DAY_AND_NIGHT_TARIFF (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tariff_code VARCHAR(255) NOT NULL UNIQUE,
    night_rate_valid_from TIME NOT NULL,
    day_rate_valid_from TIME NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS USERS (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL CHECK(role IN ('ADMIN', 'USER')),
    must_change_password BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (username)
);

CREATE INDEX IF NOT EXISTS idx_agreement_tariff_code ON AGREEMENT(tariff_code);
CREATE INDEX IF NOT EXISTS idx_day_and_night_tariff_tariff_code ON DAY_AND_NIGHT_TARIFF(tariff_code);
CREATE INDEX IF NOT EXISTS idx_unit_rate_by_half_hour_valid_from ON UNIT_RATE_BY_HALF_HOUR(valid_from);
CREATE INDEX IF NOT EXISTS idx_standing_charge_by_day_valid_from ON STANDING_CHARGE_BY_DAY(valid_from);
CREATE INDEX IF NOT EXISTS idx_usage_interval_from ON USAGE(interval_from);
CREATE INDEX IF NOT EXISTS idx_utc_to_local_utc_time ON UTC_TO_LOCAL(utc_time);
