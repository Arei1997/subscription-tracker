package com.tracker.repository;

import com.tracker.model.BillingCycle;
import com.tracker.model.Category;
import com.tracker.model.Status;
import com.tracker.model.Subscription;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SubscriptionRepositoryTest {

    private static final String URL = "jdbc:sqlite:file:testdb?mode=memory&cache=shared";

    // Held open for the lifetime of the test class to keep the in-memory DB alive.
    private static Connection keeper;

    private SubscriptionRepository repository;

    @BeforeAll
    static void openKeeper() throws Exception {
        keeper = DriverManager.getConnection(URL);
    }

    @AfterAll
    static void closeKeeper() throws Exception {
        keeper.close();
    }

    @BeforeEach
    void setUp() throws Exception {
        try (var stmt = keeper.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS subscriptions");
        }
        new DatabaseInitialiser(URL).initialise();
        repository = new SubscriptionRepository(URL);
    }

    // -------------------------------------------------------------------------
    // add / findById
    // -------------------------------------------------------------------------

    @Test
    void add_and_findById_returnsSubscription() {
        repository.add(subscription("Netflix"));
        List<Subscription> all = repository.findAll();
        assertEquals(1, all.size());

        Optional<Subscription> found = repository.findById(all.get(0).id());
        assertTrue(found.isPresent());
        assertEquals("Netflix", found.get().name());
    }

    @Test
    void findById_unknownId_returnsEmpty() {
        Optional<Subscription> result = repository.findById(999);
        assertTrue(result.isEmpty());
    }

    // -------------------------------------------------------------------------
    // findAll
    // -------------------------------------------------------------------------

    @Test
    void findAll_returnsAllSubscriptions_sortedByName() {
        repository.add(subscription("Spotify"));
        repository.add(subscription("Amazon"));
        repository.add(subscription("Netflix"));

        List<Subscription> all = repository.findAll();
        assertEquals(3, all.size());
        assertEquals("Amazon", all.get(0).name());
        assertEquals("Netflix", all.get(1).name());
        assertEquals("Spotify", all.get(2).name());
    }

    @Test
    void findAll_emptyDatabase_returnsEmptyList() {
        assertTrue(repository.findAll().isEmpty());
    }

    // -------------------------------------------------------------------------
    // findByStatus
    // -------------------------------------------------------------------------

    @Test
    void findByStatus_returnsOnlyMatchingStatus() {
        repository.add(subscription("Netflix"));
        List<Subscription> all = repository.findAll();
        Subscription netflix = all.get(0);

        Subscription cancelled = withStatus(netflix, Status.CANCELLED, LocalDate.now());
        repository.update(cancelled);

        repository.add(subscription("Spotify"));

        List<Subscription> active = repository.findByStatus(Status.ACTIVE);
        List<Subscription> cancelledList = repository.findByStatus(Status.CANCELLED);

        assertEquals(1, active.size());
        assertEquals("Spotify", active.get(0).name());
        assertEquals(1, cancelledList.size());
        assertEquals("Netflix", cancelledList.get(0).name());
    }

    // -------------------------------------------------------------------------
    // update
    // -------------------------------------------------------------------------

    @Test
    void update_changesStoredValues() {
        repository.add(subscription("Netflix"));
        Subscription original = repository.findAll().get(0);

        Subscription updated = new Subscription(
                original.id(), "Netflix Premium", Category.STREAMING,
                19.99, BillingCycle.ANNUAL, original.startDate(),
                original.renewalDate(), Status.ACTIVE, null,
                "4K plan", original.createdAt()
        );
        repository.update(updated);

        Subscription fetched = repository.findById(original.id()).orElseThrow();
        assertEquals("Netflix Premium", fetched.name());
        assertEquals(19.99, fetched.cost());
        assertEquals(BillingCycle.ANNUAL, fetched.billingCycle());
        assertEquals("4K plan", fetched.notes());
    }

    // -------------------------------------------------------------------------
    // delete
    // -------------------------------------------------------------------------

    @Test
    void delete_removesSubscription() {
        repository.add(subscription("Netflix"));
        int id = repository.findAll().get(0).id();

        repository.delete(id);

        assertTrue(repository.findById(id).isEmpty());
        assertTrue(repository.findAll().isEmpty());
    }

    // -------------------------------------------------------------------------
    // findRenewingBefore
    // -------------------------------------------------------------------------

    @Test
    void findRenewingBefore_returnsOnlyActiveWithinWindow() {
        LocalDate today = LocalDate.now();

        Subscription soon = subscriptionWithRenewal("Spotify", today.plusDays(5), Status.ACTIVE);
        Subscription later = subscriptionWithRenewal("Amazon", today.plusDays(40), Status.ACTIVE);
        Subscription cancelledSoon = subscriptionWithRenewal("Netflix", today.plusDays(3), Status.CANCELLED);

        repository.add(soon);
        repository.add(later);
        repository.add(cancelledSoon);

        List<Subscription> renewing = repository.findRenewingBefore(today.plusDays(7));

        assertEquals(1, renewing.size());
        assertEquals("Spotify", renewing.get(0).name());
    }

    // -------------------------------------------------------------------------
    // getTotalMonthlyCost
    // -------------------------------------------------------------------------

    @Test
    void getTotalMonthlyCost_normalisesAllBillingCycles() {
        repository.add(subscriptionWithCost("A", 10.00, BillingCycle.MONTHLY));  // £10/mo
        repository.add(subscriptionWithCost("B", 120.00, BillingCycle.ANNUAL));  // £120/yr = £10/mo
        repository.add(subscriptionWithCost("C", 3.00, BillingCycle.WEEKLY));    // £3/wk = £13/mo

        double total = repository.getTotalMonthlyCost();

        // 10.00 + (120/12) + (3 * 52/12) = 10 + 10 + 13 = 33.00
        assertEquals(33.00, total, 0.01);
    }

    @Test
    void getTotalMonthlyCost_excludesCancelledSubscriptions() {
        repository.add(subscriptionWithCost("Active", 10.00, BillingCycle.MONTHLY));
        repository.add(subscriptionWithCost("Cancelled", 50.00, BillingCycle.MONTHLY));

        List<Subscription> all = repository.findAll();
        Subscription cancelled = all.stream()
                .filter(s -> s.name().equals("Cancelled")).findFirst().orElseThrow();
        repository.update(withStatus(cancelled, Status.CANCELLED, LocalDate.now()));

        assertEquals(10.00, repository.getTotalMonthlyCost(), 0.01);
    }

    // -------------------------------------------------------------------------
    // getCostByCategory
    // -------------------------------------------------------------------------

    @Test
    void getCostByCategory_groupsAndSortsByMonthlyCost() {
        repository.add(subscriptionWithCategory("Netflix", 15.00, Category.STREAMING));
        repository.add(subscriptionWithCategory("Disney+", 5.00, Category.STREAMING));
        repository.add(subscriptionWithCategory("Spotify", 10.00, Category.SOFTWARE));

        var result = repository.getCostByCategory();

        assertEquals(2, result.size());
        var keys = result.keySet().toArray();
        assertEquals(Category.STREAMING, keys[0]);
        assertEquals(20.00, result.get(Category.STREAMING), 0.01);
        assertEquals(10.00, result.get(Category.SOFTWARE), 0.01);
    }

    // -------------------------------------------------------------------------
    // Nullable fields
    // -------------------------------------------------------------------------

    @Test
    void nullableFields_roundtripCorrectly() {
        Subscription s = new Subscription(
                0, "Test", Category.OTHER, 5.00, BillingCycle.MONTHLY,
                LocalDate.now(), LocalDate.now().plusMonths(1),
                Status.ACTIVE, null, null, LocalDateTime.now()
        );
        repository.add(s);

        Subscription fetched = repository.findAll().get(0);
        assertNull(fetched.cancelledDate());
        assertNull(fetched.notes());
    }

    // -------------------------------------------------------------------------
    // Builders
    // -------------------------------------------------------------------------

    private Subscription subscription(String name) {
        return new Subscription(
                0, name, Category.STREAMING, 9.99, BillingCycle.MONTHLY,
                LocalDate.now(), LocalDate.now().plusMonths(1),
                Status.ACTIVE, null, null, LocalDateTime.now()
        );
    }

    private Subscription subscriptionWithRenewal(String name, LocalDate renewal, Status status) {
        return new Subscription(
                0, name, Category.STREAMING, 9.99, BillingCycle.MONTHLY,
                LocalDate.now(), renewal, status,
                status == Status.CANCELLED ? LocalDate.now() : null,
                null, LocalDateTime.now()
        );
    }

    private Subscription subscriptionWithCost(String name, double cost, BillingCycle cycle) {
        return new Subscription(
                0, name, Category.OTHER, cost, cycle,
                LocalDate.now(), LocalDate.now().plusMonths(1),
                Status.ACTIVE, null, null, LocalDateTime.now()
        );
    }

    private Subscription subscriptionWithCategory(String name, double cost, Category category) {
        return new Subscription(
                0, name, category, cost, BillingCycle.MONTHLY,
                LocalDate.now(), LocalDate.now().plusMonths(1),
                Status.ACTIVE, null, null, LocalDateTime.now()
        );
    }

    private Subscription withStatus(Subscription s, Status status, LocalDate cancelledDate) {
        return new Subscription(
                s.id(), s.name(), s.category(), s.cost(), s.billingCycle(),
                s.startDate(), s.renewalDate(), status,
                status == Status.CANCELLED ? cancelledDate : null,
                s.notes(), s.createdAt()
        );
    }
}
