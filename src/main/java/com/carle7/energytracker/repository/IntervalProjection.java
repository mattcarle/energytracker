package com.carle7.energytracker.repository;

import java.time.LocalDateTime;

public interface IntervalProjection {
    LocalDateTime getValidFrom();
    LocalDateTime getValidTo();
}
