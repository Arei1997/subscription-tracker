package com.tracker.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record Subscription(
        int id,
        String name,
        Category category,
        double cost,
        BillingCycle billingCycle,
        LocalDate startDate,
        LocalDate renewalDate,
        Status status,
        LocalDate cancelledDate,
        String notes,
        LocalDateTime createdAt
) {}
