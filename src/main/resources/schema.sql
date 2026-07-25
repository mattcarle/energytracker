CREATE TABLE IF NOT EXISTS METER (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    mpan VARCHAR(255) NOT NULL,
    serial_number VARCHAR(255) NOT NULL,
    is_export BOOLEAN NOT NULL DEFAULT 0,
    meter_type VARCHAR(10) NOT NULL CHECK(meter_type IN ('GAS', 'ELEC')),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (mpan, serial_number)
);

CREATE TABLE IF NOT EXISTS AGREEMENT (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tariff_code VARCHAR(255) NOT NULL,
    valid_from TIMESTAMP NOT NULL,
    valid_to TIMESTAMP,
    meter_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (meter_id) REFERENCES METER(id),
    UNIQUE (meter_id, tariff_code, valid_from)
);

CREATE TABLE IF NOT EXISTS USAGE (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    interval_from TIMESTAMP NOT NULL,
    interval_to TIMESTAMP NOT NULL,
    consumption DECIMAL(10, 4) NOT NULL,
    agreement_id BIGINT NOT NULL,
    FOREIGN KEY (agreement_id) REFERENCES AGREEMENT(id)
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
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (agreement_id) REFERENCES AGREEMENT(id),
    UNIQUE (agreement_id, valid_from, payment_method)
);
