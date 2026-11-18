package com.mess;

import com.mess.adapter.BiometricDeviceAdapter;
import com.mess.adapter.DeviceType;
import com.mess.adapter.RawScan;
import com.mess.domain.Role;
import com.mess.domain.User;
import com.mess.exception.AuthenticationException;
import com.mess.exception.MessException;
import com.mess.exception.ScanRejectedException;
import com.mess.exception.ValidationException;
import com.mess.observer.AttendanceSubject;
import com.mess.observer.LoggingObserver;
import com.mess.repository.jdbc.Database;
import com.mess.repository.jdbc.JdbcScanLogRepository;
import com.mess.repository.jdbc.JdbcUserRepository;
import com.mess.service.AttendanceTracker;
import com.mess.service.AuthService;
import com.mess.service.MealSchedule;
import com.mess.service.PasswordHasher;
import com.mess.service.ScanResult;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Scanner;

/**
 * Console demo for Module 1.
 *   mvn compile exec:java -Dexec.args="--seed"   creates demo users
 *   mvn compile exec:java                        login, then simulate terminal scans
 */
public final class Main {

    private static final ZoneId MESS_ZONE = ZoneId.of("Asia/Karachi");
    private static final String TERMINAL_ID = "TERMINAL-1";
    private static final String DEMO_PASSWORD = "Mess@1234";

    private Main() { }

    public static void main(String[] args) {
        Database db = Database.fromEnv();
        JdbcUserRepository users = new JdbcUserRepository(db);
        JdbcScanLogRepository scanLogs = new JdbcScanLogRepository(db);
        AuthService auth = new AuthService(users, new PasswordHasher());

        try {
            if (args.length > 0 && args[0].equals("--seed")) {
                seed(auth);
                return;
            }
            try (AttendanceSubject subject = new AttendanceSubject()) {
                subject.subscribe(new LoggingObserver());
                AttendanceTracker tracker = new AttendanceTracker(
                        users, scanLogs, MealSchedule.defaults(), subject, MESS_ZONE);
                runConsole(auth, tracker);
            }
        } catch (MessException e) {
            System.err.println("Error: " + e.getMessage());
            if (e.getCause() != null) {
                System.err.println("Cause: " + e.getCause().getMessage());
            }
            System.exit(1);
        }
    }

    private static void seed(AuthService auth) {
        String[][] demo = {
                {"admin", "Mess Admin", "ADMIN", null},
                {"staff1", "Mess Staff", "STAFF", null},
                {"ali", "Ali Khan", "STUDENT", "RFID:04A3B2C1"},
                {"sara", "Sara Ahmed", "STUDENT", "BIO:1023"},
        };
        for (String[] d : demo) {
            try {
                auth.register(d[0], d[1], DEMO_PASSWORD, Role.valueOf(d[2]), d[3]);
                System.out.println("Created " + d[0] + " (" + d[2] + ")");
            } catch (ValidationException e) {
                System.out.println("Skipped " + d[0] + ": " + e.getMessage());
            }
        }
        System.out.println("Demo password for all users: " + DEMO_PASSWORD);
        System.out.println("Demo scans: 'RFID 04A3B2C1' (ali) and 'BIO FP-1023' (sara)");
    }

    private static void runConsole(AuthService auth, AttendanceTracker tracker) {
        Scanner in = new Scanner(System.in);
        User user = login(auth, in);
        if (user == null) {
            return;
        }
        System.out.println("Logged in as " + user.getFullName() + " (" + user.getRole() + ")");
        System.out.println("Simulate a terminal scan:  RFID <hex>  |  BIO FP-<number>  |  quit");

        BiometricDeviceAdapter reader = new BiometricDeviceAdapter();
        DateTimeFormatter timeFormat = DateTimeFormatter.ofPattern("HH:mm:ss");
        while (true) {
            System.out.print("> ");
            if (!in.hasNextLine()) {
                break;
            }
            String line = in.nextLine().trim();
            if (line.equalsIgnoreCase("quit")) {
                break;
            }
            if (line.isEmpty()) {
                continue;
            }
            String[] parts = line.split("\\s+", 2);
            DeviceType type = parseDevice(parts[0]);
            if (type == null || parts.length < 2) {
                System.out.println("Usage: RFID <hex>  |  BIO FP-<number>");
                continue;
            }
            try {
                RawScan raw = new RawScan(type, parts[1], TERMINAL_ID, Instant.now());
                ScanResult r = tracker.processScan(reader.read(raw));
                System.out.println(r.scanType() + " " + r.mealType() + " - " + r.studentName()
                        + " at " + ZonedDateTime.ofInstant(r.at(), MESS_ZONE).format(timeFormat));
            } catch (ValidationException e) {
                System.out.println("Invalid scan: " + e.getMessage());
            } catch (ScanRejectedException e) {
                System.out.println("Rejected (" + e.getReason() + "): " + e.getMessage());
            }
        }
    }

    private static User login(AuthService auth, Scanner in) {
        for (int attempt = 1; attempt <= 3; attempt++) {
            System.out.print("Username: ");
            String username = in.hasNextLine() ? in.nextLine() : "";
            System.out.print("Password: ");
            String password = in.hasNextLine() ? in.nextLine() : "";
            try {
                return auth.login(username, password);
            } catch (ValidationException | AuthenticationException e) {
                System.out.println(e.getMessage() + " (attempt " + attempt + "/3)");
            }
        }
        System.out.println("Too many failed attempts.");
        return null;
    }

    private static DeviceType parseDevice(String word) {
        return switch (word.toUpperCase()) {
            case "RFID" -> DeviceType.RFID;
            case "BIO" -> DeviceType.BIOMETRIC;
            default -> null;
        };
    }
}
