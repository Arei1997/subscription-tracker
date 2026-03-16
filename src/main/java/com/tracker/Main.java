package com.tracker;

import com.tracker.model.BillingCycle;
import com.tracker.model.Category;
import com.tracker.model.Subscription;
import com.tracker.repository.DatabaseInitialiser;
import com.tracker.repository.SubscriptionRepository;
import com.tracker.service.SubscriptionService;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

public class Main {

    private static final String DB_URL = "jdbc:sqlite:subscriptions.db";

    public static void main(String[] args) {
        new DatabaseInitialiser(DB_URL).initialise();
        SubscriptionService service = new SubscriptionService(new SubscriptionRepository(DB_URL));
        Scanner scanner = new Scanner(System.in);
        run(service, scanner);
    }

    static void run(SubscriptionService service, Scanner scanner) {
        while (true) {
            printMenu();
            String choice = scanner.nextLine().trim();
            System.out.println();

            try {
                switch (choice) {
                    case "1"  -> handleAdd(service, scanner);
                    case "2"  -> handleList(service);
                    case "3"  -> handleListActive(service);
                    case "4"  -> handleListCancelled(service);
                    case "5"  -> handleUpdate(service, scanner);
                    case "6"  -> handleCancel(service, scanner);
                    case "7"  -> handleReactivate(service, scanner);
                    case "8"  -> handleDelete(service, scanner);
                    case "9"  -> handleUpcoming(service, scanner);
                    case "10" -> handleSummary(service);
                    case "0"  -> { System.out.println("Goodbye."); return; }
                    default   -> System.out.println("Invalid option — enter a number from the menu.");
                }
            } catch (IllegalArgumentException | IllegalStateException e) {
                System.out.println("Error: " + e.getMessage());
            }

            System.out.println();
        }
    }

    // -------------------------------------------------------------------------
    // Handlers
    // -------------------------------------------------------------------------

    private static void handleAdd(SubscriptionService service, Scanner scanner) {
        System.out.println("=== Add Subscription ===");
        String name = prompt(scanner, "Name: ");
        Category category = promptEnum(scanner, Category.class, "Category");
        double cost = promptDouble(scanner, "Cost: ");
        BillingCycle billingCycle = promptEnum(scanner, BillingCycle.class, "Billing cycle");
        LocalDate startDate = promptDate(scanner, "Start date (YYYY-MM-DD) [blank = today]: ", true);
        String notes = prompt(scanner, "Notes (optional): ");

        service.add(name, category, cost, billingCycle, startDate, notes);
        System.out.println("Subscription added.");
    }

    private static void handleList(SubscriptionService service) {
        List<Subscription> all = service.getAll();
        if (all.isEmpty()) {
            System.out.println("No subscriptions found.");
            return;
        }
        System.out.println("=== All Subscriptions ===");
        printTable(all);
    }

    private static void handleListActive(SubscriptionService service) {
        List<Subscription> active = service.getActive();
        if (active.isEmpty()) {
            System.out.println("No active subscriptions.");
            return;
        }
        System.out.println("=== Active Subscriptions ===");
        printTable(active);
    }

    private static void handleListCancelled(SubscriptionService service) {
        List<Subscription> cancelled = service.getCancelled();
        if (cancelled.isEmpty()) {
            System.out.println("No cancelled subscriptions.");
            return;
        }
        System.out.println("=== Cancelled Subscriptions ===");
        printTable(cancelled);
    }

    private static void handleUpdate(SubscriptionService service, Scanner scanner) {
        int id = promptInt(scanner, "Subscription ID to update: ");
        service.findById(id).ifPresentOrElse(s -> {
            System.out.println("Editing: " + s.name());
            String name = prompt(scanner, "New name [" + s.name() + "]: ");
            if (name.isBlank()) name = s.name();
            Category category = promptEnumOrKeep(scanner, Category.class, "Category", s.category());
            double cost = promptDoubleOrKeep(scanner, "Cost [" + s.cost() + "]: ", s.cost());
            BillingCycle billingCycle = promptEnumOrKeep(scanner, BillingCycle.class, "Billing cycle", s.billingCycle());
            LocalDate startDate = promptDateOrKeep(scanner, "Start date [" + s.startDate() + "]: ", s.startDate());
            String notes = prompt(scanner, "Notes [" + (s.notes() != null ? s.notes() : "") + "]: ");
            if (notes.isBlank()) notes = s.notes();

            service.update(id, name, category, cost, billingCycle, startDate, notes);
            System.out.println("Subscription updated.");
        }, () -> System.out.println("No subscription found with id " + id));
    }

    private static void handleCancel(SubscriptionService service, Scanner scanner) {
        int id = promptInt(scanner, "Subscription ID to cancel: ");
        service.cancel(id);
        System.out.println("Subscription cancelled.");
    }

    private static void handleReactivate(SubscriptionService service, Scanner scanner) {
        int id = promptInt(scanner, "Subscription ID to reactivate: ");
        service.reactivate(id);
        System.out.println("Subscription reactivated.");
    }

    private static void handleDelete(SubscriptionService service, Scanner scanner) {
        int id = promptInt(scanner, "Subscription ID to delete: ");
        System.out.print("Are you sure? (yes/no): ");
        String confirm = scanner.nextLine().trim();
        if (confirm.equalsIgnoreCase("yes")) {
            service.delete(id);
            System.out.println("Subscription deleted.");
        } else {
            System.out.println("Cancelled.");
        }
    }

    private static void handleUpcoming(SubscriptionService service, Scanner scanner) {
        int days = promptInt(scanner, "Show renewals in the next how many days? ");
        List<Subscription> upcoming = service.getRenewingWithinDays(days);
        if (upcoming.isEmpty()) {
            System.out.println("No renewals in the next " + days + " days.");
            return;
        }
        System.out.println("=== Renewals in next " + days + " days ===");
        printTable(upcoming);
    }

    private static void handleSummary(SubscriptionService service) {
        double monthly = service.getTotalMonthlyCost();
        double annual = service.getTotalAnnualCost();
        Map<com.tracker.model.Category, Double> byCategory = service.getCostByCategory();

        System.out.println("=== Cost Summary ===");
        System.out.printf("  Monthly total : £%.2f%n", monthly);
        System.out.printf("  Annual total  : £%.2f%n", annual);
        System.out.println();
        System.out.println("  By category:");
        if (byCategory.isEmpty()) {
            System.out.println("    (no active subscriptions)");
        } else {
            byCategory.forEach((cat, cost) ->
                    System.out.printf("    %-12s £%.2f/mo%n", cat, cost));
        }
    }

    // -------------------------------------------------------------------------
    // Display
    // -------------------------------------------------------------------------

    private static void printMenu() {
        System.out.println("=== Subscription Tracker ===");
        System.out.println(" 1. Add subscription");
        System.out.println(" 2. List all");
        System.out.println(" 3. List active");
        System.out.println(" 4. List cancelled");
        System.out.println(" 5. Update subscription");
        System.out.println(" 6. Cancel subscription");
        System.out.println(" 7. Reactivate subscription");
        System.out.println(" 8. Delete subscription");
        System.out.println(" 9. Upcoming renewals");
        System.out.println("10. Cost summary");
        System.out.println(" 0. Exit");
        System.out.print("Choice: ");
    }

    private static void printTable(List<Subscription> list) {
        System.out.printf("%-4s %-20s %-10s %-8s %-8s %-12s %-12s %-10s%n",
                "ID", "Name", "Category", "Cost", "Cycle", "Start", "Renewal", "Status");
        System.out.println("-".repeat(90));
        for (Subscription s : list) {
            System.out.printf("%-4d %-20s %-10s £%-7.2f %-8s %-12s %-12s %-10s%n",
                    s.id(),
                    truncate(s.name(), 20),
                    s.category(),
                    s.cost(),
                    s.billingCycle(),
                    s.startDate(),
                    s.renewalDate(),
                    s.status());
        }
    }

    // -------------------------------------------------------------------------
    // Input helpers
    // -------------------------------------------------------------------------

    private static String prompt(Scanner scanner, String message) {
        System.out.print(message);
        return scanner.nextLine().trim();
    }

    private static int promptInt(Scanner scanner, String message) {
        while (true) {
            System.out.print(message);
            String input = scanner.nextLine().trim();
            try {
                return Integer.parseInt(input);
            } catch (NumberFormatException e) {
                System.out.println("Please enter a whole number.");
            }
        }
    }

    private static double promptDouble(Scanner scanner, String message) {
        while (true) {
            System.out.print(message);
            String input = scanner.nextLine().trim();
            try {
                return Double.parseDouble(input);
            } catch (NumberFormatException e) {
                System.out.println("Please enter a valid number (e.g. 9.99).");
            }
        }
    }

    private static double promptDoubleOrKeep(Scanner scanner, String message, double current) {
        System.out.print(message);
        String input = scanner.nextLine().trim();
        if (input.isBlank()) return current;
        try {
            return Double.parseDouble(input);
        } catch (NumberFormatException e) {
            System.out.println("Invalid input — keeping current value.");
            return current;
        }
    }

    private static LocalDate promptDate(Scanner scanner, String message, boolean allowBlankForToday) {
        while (true) {
            System.out.print(message);
            String input = scanner.nextLine().trim();
            if (allowBlankForToday && input.isBlank()) return LocalDate.now();
            try {
                return LocalDate.parse(input);
            } catch (DateTimeParseException e) {
                System.out.println("Invalid date — use YYYY-MM-DD format.");
            }
        }
    }

    private static LocalDate promptDateOrKeep(Scanner scanner, String message, LocalDate current) {
        System.out.print(message);
        String input = scanner.nextLine().trim();
        if (input.isBlank()) return current;
        try {
            return LocalDate.parse(input);
        } catch (DateTimeParseException e) {
            System.out.println("Invalid date — keeping current value.");
            return current;
        }
    }

    private static <E extends Enum<E>> E promptEnum(Scanner scanner, Class<E> type, String label) {
        E[] values = type.getEnumConstants();
        while (true) {
            System.out.print(label + " (");
            for (int i = 0; i < values.length; i++) {
                System.out.print((i + 1) + "=" + values[i].name());
                if (i < values.length - 1) System.out.print(", ");
            }
            System.out.print("): ");
            String input = scanner.nextLine().trim();
            try {
                int idx = Integer.parseInt(input) - 1;
                if (idx >= 0 && idx < values.length) return values[idx];
            } catch (NumberFormatException ignored) {}
            System.out.println("Enter a number between 1 and " + values.length + ".");
        }
    }

    private static <E extends Enum<E>> E promptEnumOrKeep(Scanner scanner, Class<E> type, String label, E current) {
        E[] values = type.getEnumConstants();
        System.out.print(label + " [" + current.name() + "] (");
        for (int i = 0; i < values.length; i++) {
            System.out.print((i + 1) + "=" + values[i].name());
            if (i < values.length - 1) System.out.print(", ");
        }
        System.out.print(", blank=keep): ");
        String input = scanner.nextLine().trim();
        if (input.isBlank()) return current;
        try {
            int idx = Integer.parseInt(input) - 1;
            if (idx >= 0 && idx < values.length) return values[idx];
        } catch (NumberFormatException ignored) {}
        System.out.println("Invalid input — keeping current value.");
        return current;
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
