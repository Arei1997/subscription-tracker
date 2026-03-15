package com.tracker.repository;

import com.tracker.model.BillingCycle;
import com.tracker.model.Category;
import com.tracker.model.Status;
import com.tracker.model.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class SubscriptionRepository {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionRepository.class);

    private final String url;

    public SubscriptionRepository(String url) {
        this.url = url;
    }

    // -------------------------------------------------------------------------
    // Write operations
    // -------------------------------------------------------------------------

    public void add(Subscription s) {
        String sql = """
                INSERT INTO subscriptions
                    (name, category, cost, billing_cycle, start_date, renewal_date,
                     status, cancelled_date, notes, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (Connection conn = DriverManager.getConnection(url);
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, s.name());
            ps.setString(2, s.category().name());
            ps.setDouble(3, s.cost());
            ps.setString(4, s.billingCycle().name());
            ps.setString(5, s.startDate().toString());
            ps.setString(6, s.renewalDate().toString());
            ps.setString(7, s.status().name());
            ps.setString(8, s.cancelledDate() != null ? s.cancelledDate().toString() : null);
            ps.setString(9, s.notes());
            ps.setString(10, s.createdAt().toString());

            ps.executeUpdate();
            log.info("Added subscription: {}", s.name());

        } catch (SQLException e) {
            throw new RuntimeException("Failed to add subscription", e);
        }
    }

    public void update(Subscription s) {
        String sql = """
                UPDATE subscriptions
                SET name = ?, category = ?, cost = ?, billing_cycle = ?,
                    start_date = ?, renewal_date = ?, status = ?,
                    cancelled_date = ?, notes = ?
                WHERE id = ?
                """;

        try (Connection conn = DriverManager.getConnection(url);
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, s.name());
            ps.setString(2, s.category().name());
            ps.setDouble(3, s.cost());
            ps.setString(4, s.billingCycle().name());
            ps.setString(5, s.startDate().toString());
            ps.setString(6, s.renewalDate().toString());
            ps.setString(7, s.status().name());
            ps.setString(8, s.cancelledDate() != null ? s.cancelledDate().toString() : null);
            ps.setString(9, s.notes());
            ps.setInt(10, s.id());

            ps.executeUpdate();
            log.info("Updated subscription id={}", s.id());

        } catch (SQLException e) {
            throw new RuntimeException("Failed to update subscription", e);
        }
    }

    public void delete(int id) {
        try (Connection conn = DriverManager.getConnection(url);
             PreparedStatement ps = conn.prepareStatement(
                     "DELETE FROM subscriptions WHERE id = ?")) {

            ps.setInt(1, id);
            ps.executeUpdate();
            log.info("Deleted subscription id={}", id);

        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete subscription", e);
        }
    }

    // -------------------------------------------------------------------------
    // Read operations
    // -------------------------------------------------------------------------

    public Optional<Subscription> findById(int id) {
        try (Connection conn = DriverManager.getConnection(url);
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM subscriptions WHERE id = ?")) {

            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(map(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find subscription", e);
        }
        return Optional.empty();
    }

    public List<Subscription> findAll() {
        List<Subscription> result = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(url);
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM subscriptions ORDER BY name");
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                result.add(map(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to list subscriptions", e);
        }
        return result;
    }

    public List<Subscription> findByStatus(Status status) {
        List<Subscription> result = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(url);
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM subscriptions WHERE status = ? ORDER BY name")) {

            ps.setString(1, status.name());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(map(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to filter subscriptions by status", e);
        }
        return result;
    }

    public List<Subscription> findRenewingBefore(LocalDate cutoff) {
        List<Subscription> result = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(url);
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM subscriptions WHERE status = 'ACTIVE' AND renewal_date <= ? ORDER BY renewal_date")) {

            ps.setString(1, cutoff.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(map(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to query upcoming renewals", e);
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Aggregates
    // -------------------------------------------------------------------------

    public double getTotalMonthlyCost() {
        String sql = """
                SELECT SUM(CASE billing_cycle
                    WHEN 'MONTHLY' THEN cost
                    WHEN 'ANNUAL'  THEN cost / 12.0
                    WHEN 'WEEKLY'  THEN cost * 52.0 / 12.0
                END) AS monthly_total
                FROM subscriptions
                WHERE status = 'ACTIVE'
                """;

        try (Connection conn = DriverManager.getConnection(url);
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            if (rs.next()) {
                return rs.getDouble("monthly_total");
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to calculate monthly cost", e);
        }
        return 0.0;
    }

    public Map<Category, Double> getCostByCategory() {
        String sql = """
                SELECT category,
                       SUM(CASE billing_cycle
                           WHEN 'MONTHLY' THEN cost
                           WHEN 'ANNUAL'  THEN cost / 12.0
                           WHEN 'WEEKLY'  THEN cost * 52.0 / 12.0
                       END) AS monthly_total
                FROM subscriptions
                WHERE status = 'ACTIVE'
                GROUP BY category
                ORDER BY monthly_total DESC
                """;

        Map<Category, Double> result = new LinkedHashMap<>();
        try (Connection conn = DriverManager.getConnection(url);
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                Category cat = Category.valueOf(rs.getString("category"));
                result.put(cat, rs.getDouble("monthly_total"));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to calculate cost by category", e);
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Mapping
    // -------------------------------------------------------------------------

    private Subscription map(ResultSet rs) throws SQLException {
        String cancelledDateStr = rs.getString("cancelled_date");
        return new Subscription(
                rs.getInt("id"),
                rs.getString("name"),
                Category.valueOf(rs.getString("category")),
                rs.getDouble("cost"),
                BillingCycle.valueOf(rs.getString("billing_cycle")),
                LocalDate.parse(rs.getString("start_date")),
                LocalDate.parse(rs.getString("renewal_date")),
                Status.valueOf(rs.getString("status")),
                cancelledDateStr != null ? LocalDate.parse(cancelledDateStr) : null,
                rs.getString("notes"),
                LocalDateTime.parse(rs.getString("created_at"))
        );
    }
}
