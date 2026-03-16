package com.tracker.service;

import com.tracker.model.BillingCycle;
import com.tracker.model.Category;
import com.tracker.model.Status;
import com.tracker.model.Subscription;
import com.tracker.repository.SubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

    @Mock
    private SubscriptionRepository repository;

    private SubscriptionService service;

    @BeforeEach
    void setUp() {
        service = new SubscriptionService(repository);
    }

    // -------------------------------------------------------------------------
    // add
    // -------------------------------------------------------------------------

    @Test
    void add_validInput_callsRepositoryWithCorrectFields() {
        LocalDate start = LocalDate.of(2026, 1, 1);

        service.add("Netflix", Category.STREAMING, 9.99, BillingCycle.MONTHLY, start, "4K plan");

        ArgumentCaptor<Subscription> captor = ArgumentCaptor.forClass(Subscription.class);
        verify(repository).add(captor.capture());

        Subscription saved = captor.getValue();
        assertEquals("Netflix", saved.name());
        assertEquals(Category.STREAMING, saved.category());
        assertEquals(9.99, saved.cost());
        assertEquals(BillingCycle.MONTHLY, saved.billingCycle());
        assertEquals(start, saved.startDate());
        assertEquals(start.plusMonths(1), saved.renewalDate());
        assertEquals(Status.ACTIVE, saved.status());
        assertNull(saved.cancelledDate());
        assertEquals("4K plan", saved.notes());
    }

    @Test
    void add_blankName_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () ->
                service.add("  ", Category.STREAMING, 9.99, BillingCycle.MONTHLY, LocalDate.now(), null));
        verifyNoInteractions(repository);
    }

    @Test
    void add_zeroCost_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () ->
                service.add("Netflix", Category.STREAMING, 0, BillingCycle.MONTHLY, LocalDate.now(), null));
        verifyNoInteractions(repository);
    }

    @Test
    void add_negativeCost_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () ->
                service.add("Netflix", Category.STREAMING, -5.00, BillingCycle.MONTHLY, LocalDate.now(), null));
        verifyNoInteractions(repository);
    }

    @Test
    void add_blankNotes_savedAsNull() {
        service.add("Netflix", Category.STREAMING, 9.99, BillingCycle.MONTHLY, LocalDate.now(), "   ");

        ArgumentCaptor<Subscription> captor = ArgumentCaptor.forClass(Subscription.class);
        verify(repository).add(captor.capture());
        assertNull(captor.getValue().notes());
    }

    // -------------------------------------------------------------------------
    // update
    // -------------------------------------------------------------------------

    @Test
    void update_validInput_preservesStatusAndCreatedAt() {
        LocalDateTime createdAt = LocalDateTime.of(2025, 6, 1, 10, 0);
        Subscription existing = activeSubscription(1, "Netflix", createdAt);
        when(repository.findById(1)).thenReturn(Optional.of(existing));

        service.update(1, "Netflix Premium", Category.STREAMING, 15.99,
                BillingCycle.ANNUAL, LocalDate.of(2026, 1, 1), null);

        ArgumentCaptor<Subscription> captor = ArgumentCaptor.forClass(Subscription.class);
        verify(repository).update(captor.capture());

        Subscription updated = captor.getValue();
        assertEquals("Netflix Premium", updated.name());
        assertEquals(15.99, updated.cost());
        assertEquals(Status.ACTIVE, updated.status());
        assertEquals(createdAt, updated.createdAt());
    }

    @Test
    void update_unknownId_throwsIllegalArgumentException() {
        when(repository.findById(99)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () ->
                service.update(99, "X", Category.OTHER, 1.0, BillingCycle.MONTHLY, LocalDate.now(), null));
        verify(repository, never()).update(any());
    }

    // -------------------------------------------------------------------------
    // delete
    // -------------------------------------------------------------------------

    @Test
    void delete_existingId_callsRepositoryDelete() {
        when(repository.findById(1)).thenReturn(Optional.of(activeSubscription(1, "Netflix", LocalDateTime.now())));
        service.delete(1);
        verify(repository).delete(1);
    }

    @Test
    void delete_unknownId_throwsIllegalArgumentException() {
        when(repository.findById(99)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> service.delete(99));
        verify(repository, never()).delete(anyInt());
    }

    // -------------------------------------------------------------------------
    // cancel
    // -------------------------------------------------------------------------

    @Test
    void cancel_activeSubscription_setsStatusAndCancelledDate() {
        when(repository.findById(1)).thenReturn(Optional.of(activeSubscription(1, "Netflix", LocalDateTime.now())));

        service.cancel(1);

        ArgumentCaptor<Subscription> captor = ArgumentCaptor.forClass(Subscription.class);
        verify(repository).update(captor.capture());

        Subscription cancelled = captor.getValue();
        assertEquals(Status.CANCELLED, cancelled.status());
        assertNotNull(cancelled.cancelledDate());
    }

    @Test
    void cancel_alreadyCancelled_throwsIllegalStateException() {
        when(repository.findById(1)).thenReturn(Optional.of(cancelledSubscription(1, "Netflix")));
        assertThrows(IllegalStateException.class, () -> service.cancel(1));
        verify(repository, never()).update(any());
    }

    // -------------------------------------------------------------------------
    // reactivate
    // -------------------------------------------------------------------------

    @Test
    void reactivate_cancelledSubscription_setsActiveAndClearsDate() {
        when(repository.findById(1)).thenReturn(Optional.of(cancelledSubscription(1, "Netflix")));

        service.reactivate(1);

        ArgumentCaptor<Subscription> captor = ArgumentCaptor.forClass(Subscription.class);
        verify(repository).update(captor.capture());

        Subscription reactivated = captor.getValue();
        assertEquals(Status.ACTIVE, reactivated.status());
        assertNull(reactivated.cancelledDate());
        assertNotNull(reactivated.renewalDate());
    }

    @Test
    void reactivate_alreadyActive_throwsIllegalStateException() {
        when(repository.findById(1)).thenReturn(Optional.of(activeSubscription(1, "Netflix", LocalDateTime.now())));
        assertThrows(IllegalStateException.class, () -> service.reactivate(1));
        verify(repository, never()).update(any());
    }

    // -------------------------------------------------------------------------
    // getTotalAnnualCost
    // -------------------------------------------------------------------------

    @Test
    void getTotalAnnualCost_returnsMonthlyTimestwelve() {
        when(repository.getTotalMonthlyCost()).thenReturn(10.0);
        assertEquals(120.0, service.getTotalAnnualCost());
    }

    // -------------------------------------------------------------------------
    // calculateRenewalDate
    // -------------------------------------------------------------------------

    @Test
    void calculateRenewalDate_monthly_addOneMonth() {
        LocalDate from = LocalDate.of(2026, 1, 15);
        assertEquals(LocalDate.of(2026, 2, 15), SubscriptionService.calculateRenewalDate(from, BillingCycle.MONTHLY));
    }

    @Test
    void calculateRenewalDate_annual_addOneYear() {
        LocalDate from = LocalDate.of(2026, 1, 15);
        assertEquals(LocalDate.of(2027, 1, 15), SubscriptionService.calculateRenewalDate(from, BillingCycle.ANNUAL));
    }

    @Test
    void calculateRenewalDate_weekly_addOneWeek() {
        LocalDate from = LocalDate.of(2026, 1, 15);
        assertEquals(LocalDate.of(2026, 1, 22), SubscriptionService.calculateRenewalDate(from, BillingCycle.WEEKLY));
    }

    // -------------------------------------------------------------------------
    // Builders
    // -------------------------------------------------------------------------

    private Subscription activeSubscription(int id, String name, LocalDateTime createdAt) {
        return new Subscription(
                id, name, Category.STREAMING, 9.99, BillingCycle.MONTHLY,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 1),
                Status.ACTIVE, null, null, createdAt
        );
    }

    private Subscription cancelledSubscription(int id, String name) {
        return new Subscription(
                id, name, Category.STREAMING, 9.99, BillingCycle.MONTHLY,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 1),
                Status.CANCELLED, LocalDate.of(2026, 3, 1), null, LocalDateTime.now()
        );
    }
}
