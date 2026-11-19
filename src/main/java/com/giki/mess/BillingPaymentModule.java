package com.giki.mess;

import com.giki.mess.api.BillingInputValidator;
import com.giki.mess.api.BillingPaymentApi;
import com.giki.mess.billing.config.TariffConfiguration;
import com.giki.mess.billing.service.AttendanceDataSource;
import com.giki.mess.billing.service.BillingService;
import com.giki.mess.billing.service.BillingServiceImpl;
import com.giki.mess.billing.strategy.BillingStrategies;
import com.giki.mess.observer.AttendanceSubject;
import com.giki.mess.observer.BillingObserver;
import com.giki.mess.payment.factory.PaymentProcessorFactory;
import com.giki.mess.payment.gateway.BankTransferProcessor;
import com.giki.mess.payment.gateway.EasyPaisaProcessor;
import com.giki.mess.payment.gateway.JazzCashProcessor;
import com.giki.mess.payment.idempotency.IdempotencyManager;
import com.giki.mess.payment.model.PaymentChannel;
import com.giki.mess.payment.service.PaymentService;
import com.giki.mess.payment.service.PaymentServiceImpl;
import com.giki.mess.repository.DatabaseConfig;
import com.giki.mess.repository.IBillRepository;
import com.giki.mess.repository.ILedgerRepository;
import com.giki.mess.repository.IPaymentRepository;
import com.giki.mess.repository.persistence.PostgresBillRepository;
import com.giki.mess.repository.persistence.PostgresDatabase;
import com.giki.mess.repository.persistence.PostgresLedgerRepository;
import com.giki.mess.repository.persistence.PostgresPaymentRepository;
import java.time.Clock;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * COMPOSITION ROOT: the one place where concrete classes are chosen and plugged together
 * (Dependency Injection by hand). Everything else depends on interfaces only.
 *
 * <p>The Attendance module (owned by a teammate) is connected through two small ports:
 * AttendanceDataSource (what Billing reads) and AttendanceSubject (where scans are published).
 */
public final class BillingPaymentModule implements AutoCloseable {

    private final PostgresDatabase database;
    private final ILedgerRepository ledgers;
    private final IBillRepository bills;
    private final IPaymentRepository payments;
    private final BillingService billing;
    private final PaymentService paymentService;
    private final BillingPaymentApi api;
    private final AttendanceSubject attendanceSubject;
    private final ExecutorService observerPool;
    private final JazzCashProcessor jazzCash;
    private final EasyPaisaProcessor easyPaisa;
    private final BankTransferProcessor bankTransfer;

    private BillingPaymentModule(DatabaseConfig databaseConfig, Clock clock, Map<PaymentChannel, String> secrets,
                                 AttendanceDataSource attendance) {
        this.database = PostgresDatabase.connect(databaseConfig);
        database.initialize();
        this.ledgers = new PostgresLedgerRepository(database);
        this.bills = new PostgresBillRepository(database);
        this.payments = new PostgresPaymentRepository(database);

        TariffConfiguration tariff = TariffConfiguration.defaults();
        this.billing = new BillingServiceImpl(ledgers, bills, attendance, tariff,
            BillingStrategies.defaults(tariff));

        this.jazzCash = new JazzCashProcessor(secrets.get(PaymentChannel.JAZZCASH), clock);
        this.easyPaisa = new EasyPaisaProcessor(secrets.get(PaymentChannel.EASYPAISA), clock);
        this.bankTransfer = new BankTransferProcessor(secrets.get(PaymentChannel.BANK_TRANSFER), clock);
        Map<PaymentChannel, com.giki.mess.payment.gateway.IPaymentProcessor> processors =
            new EnumMap<>(PaymentChannel.class);
        processors.put(PaymentChannel.JAZZCASH, jazzCash);
        processors.put(PaymentChannel.EASYPAISA, easyPaisa);
        processors.put(PaymentChannel.BANK_TRANSFER, bankTransfer);
        this.paymentService = new PaymentServiceImpl(new PaymentProcessorFactory(processors),
            payments, bills, ledgers, new IdempotencyManager());

        this.api = new BillingPaymentApi(billing, paymentService, new BillingInputValidator());

        this.observerPool = Executors.newFixedThreadPool(4, runnable -> {
            Thread t = new Thread(runnable, "scan-observer");
            t.setDaemon(true);
            return t;
        });
        this.attendanceSubject = new AttendanceSubject(observerPool);
        this.attendanceSubject.register(new BillingObserver(billing));
    }

    /**
     * Connects to PostgreSQL, creates the tables if needed and wires every component.
     *
     * @param secrets one shared secret per gateway (used to verify callback signatures)
     * @throws com.giki.mess.repository.PersistenceException if the database cannot be reached
     */
    public static BillingPaymentModule create(DatabaseConfig databaseConfig, Clock clock,
                                              Map<PaymentChannel, String> secrets,
                                              AttendanceDataSource attendance) {
        return new BillingPaymentModule(databaseConfig, clock, secrets, attendance);
    }

    public BillingPaymentApi api() {
        return api;
    }

    public AttendanceSubject attendanceSubject() {
        return attendanceSubject;
    }

    public ILedgerRepository ledgers() {
        return ledgers;
    }

    public IBillRepository bills() {
        return bills;
    }

    public IPaymentRepository payments() {
        return payments;
    }

    public JazzCashProcessor jazzCash() {
        return jazzCash;
    }

    @Override
    public void close() {
        attendanceSubject.close();
        database.close();
    }
}
