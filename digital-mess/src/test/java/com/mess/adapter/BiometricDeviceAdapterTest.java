package com.mess.adapter;

import com.mess.exception.ValidationException;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BiometricDeviceAdapterTest {

    private static final Instant NOW = Instant.parse("2026-11-19T08:00:00Z");
    private final BiometricDeviceAdapter adapter = new BiometricDeviceAdapter();

    @Test
    void rfidPayloadIsNormalizedToUpperCase() {
        ScanEvent e = adapter.read(new RawScan(DeviceType.RFID, " 04a3b2c1 ", "T1", NOW));
        assertEquals("RFID:04A3B2C1", e.credentialId());
        assertEquals("T1", e.deviceId());
        assertEquals(NOW, e.scannedAt());
    }

    @Test
    void biometricPayloadIsNormalized() {
        ScanEvent e = adapter.read(new RawScan(DeviceType.BIOMETRIC, "FP-1023", "T1", NOW));
        assertEquals("BIO:1023", e.credentialId());
    }

    @Test
    void rfidLengthBoundaries() {
        // 8 and 16 hex chars are valid, 7 and 17 are not
        adapter.read(new RawScan(DeviceType.RFID, "A".repeat(8), "T1", NOW));
        adapter.read(new RawScan(DeviceType.RFID, "A".repeat(16), "T1", NOW));
        assertThrows(ValidationException.class,
                () -> adapter.read(new RawScan(DeviceType.RFID, "A".repeat(7), "T1", NOW)));
        assertThrows(ValidationException.class,
                () -> adapter.read(new RawScan(DeviceType.RFID, "A".repeat(17), "T1", NOW)));
    }

    @Test
    void malformedPayloadsAreRejected() {
        assertThrows(ValidationException.class,
                () -> adapter.read(new RawScan(DeviceType.RFID, "ZZZZZZZZ", "T1", NOW)));
        assertThrows(ValidationException.class,
                () -> adapter.read(new RawScan(DeviceType.BIOMETRIC, "1023", "T1", NOW)));
        assertThrows(ValidationException.class,
                () -> adapter.read(new RawScan(DeviceType.BIOMETRIC, null, "T1", NOW)));
    }

    @Test
    void missingFieldsAreRejected() {
        assertThrows(ValidationException.class, () -> adapter.read(null));
        assertThrows(ValidationException.class,
                () -> adapter.read(new RawScan(null, "FP-1", "T1", NOW)));
        assertThrows(ValidationException.class,
                () -> adapter.read(new RawScan(DeviceType.BIOMETRIC, "FP-1", " ", NOW)));
        assertThrows(ValidationException.class,
                () -> adapter.read(new RawScan(DeviceType.BIOMETRIC, "FP-1", "T1", null)));
    }
}
