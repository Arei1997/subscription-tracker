package com.tracker.service;

import com.tracker.model.BillingCycle;
import com.tracker.model.Category;
import com.tracker.model.Status;
import com.tracker.model.Subscription;
import com.tracker.repository.SubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class SubscriptionService {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionService.class);

    private final SubscriptionRepository repository;

    public SubscriptionService(SubscriptionRepository repository) {
        this.repository = repository;
    }

    // -------------------------------------------------------------------------
    // Add
    // -------------------------------------------------------------------------

    public void add(String name, Category category, double cost,
                    BillingCycle billingCycle, LocalDate startDate, String notes) {

        validateName(name);
        validateCost(cost);

        LocalDate renewalDate = calculateRenewalDate(startDate, billingCycle);

        Subscription s = new Subscription(
                0,
                name.trim(),
                category,
                cost,
                billingCycle,
                startDate,
                renewalDate,
                Status.ACTIVE,
                null,
                notes == null || notes.isBlank() ? null : notes.trim(),
                LocalDateTime.now()
        );

        repository.add(s);
        log.info("Service: added subscription '{}'", name);
    }

    // -------------------------------------------------------------------------
    // Update
    // -------------------------------------------------------------------------

    public void update(int id, String name, Category category, double cost,
                       BillingCycle billingCycle, LocalDate startDate, String notes) {

        Subscription existing = getOrThrow(id);
        validateName(name);
        validateCost(cost);

        LocalDate renewalDate = calculateRenewalDate(startDate, billingCycle);

        Subscription updated = new Subscription(
                id,
                name.trim(),
                category,
                cost,
                billingCycle,
                startDate,
                renewalDate,
                existing.status(),
                existing.cancelledDate(),
                notes == null || notes.isBlank() ? null : notes.trim(),
                existing.createdAt()
        );

        repository.update(updated);
        log.info("Service: updated subscription id={}", id);
    }

    // -------------------------------------------------------------------------
    // Delete
    // -------------------------------------------------------------------------

    public void delete(int id) {
        getOrThrow(id);
        repository.delete(id);
        log.info("Service: deleted subscription id={}", id);
    }

    // -------------------------------------------------------------------------
    // Cancel / reactivate
    // -------------------------------------------------------------------------

    public void cancel(int id) {
        Subscription s = getOrThrow(id);
        if (s.status() == Status.CANCELLED) {
            throw new IllegalStateException("Subscription is already cancelled");
        }

        Subscription cancelled = new Subscription(
                s.id(), s.name(), s.category(), s.cost(), s.billingCycle(),
                s.startDate(), s.renewalDate(), Status.CANCELLED,
                LocalDate.now(), s.notes(), s.createdAt()
        );

        repository.update(cancelled);
        log.info("Service: cancelled subscription id={}", id);
    }

    public void reactivate(int id) {
        Subscription s = getOrThrow(id);
        if (s.status() == Status.ACTIVE) {
            throw new IllegalStateException("Subscription is already active");
        }

        LocalDate renewalDate = calculateRenewalDate(LocalDate.now(), s.billingCycle());

        Subscription reactivated = new Subscription(
                s.id(), s.name(), s.category(), s.cost(), s.billingCycle(),
                s.startDate(), renewalDate, Status.ACTIVE,
                null, s.notes(), s.createdAt()
        );

        repository.update(reactivated);
        log.info("Service: reactivated subscription id={}", id);
    }

    // -------------------------------------------------------------------------
    // Queries
    // -------------------------------------------------------------------------

    public List<Subscription> getAll() {
        return repository.findAll();
    }

    public List<Subscription> getActive() {
        return repository.findByStatus(Status.ACTIVE);
    }

    public List<Subscription> getCancelled() {
        return repository.findByStatus(Status.CANCELLED);
    }

    public Optional<Subscription> findById(int id) {
        return repository.findById(id);
    }

    public List<Subscription> getRenewingWithinDays(int days) {
        return repository.findRenewingBefore(LocalDate.now().plusDays(days));
    }

    // -------------------------------------------------------------------------
    // Aggregates
    // -------------------------------------------------------------------------

    public double getTotalMonthlyCost() {
        return repository.getTotalMonthlyCost();
    }

    public double getTotalAnnualCost() {
        return repository.getTotalMonthlyCost() * 12;
    }

    public Map<Category, Double> getCostByCategory() {
        return repository.getCostByCategory();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Subscription getOrThrow(int id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No subscription found with id " + id));
    }

    private void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Name must not be blank");
        }
    }

    private void validateCost(double cost) {
        if (cost <= 0) {
            throw new IllegalArgumentException("Cost must be greater than zero");
        }
    }

    static LocalDate calculateRenewalDate(LocalDate from, BillingCycle billingCycle) {
        return switch (billingCycle) {
            case MONTHLY -> from.plusMonths(1);
            case ANNUAL  -> from.plusYears(1);
            case WEEKLY  -> from.plusWeeks(1);
        };
    }
}
