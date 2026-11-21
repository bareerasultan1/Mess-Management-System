package com.mess.adapter;

import com.mess.exception.ValidationException;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Adapter pattern: hides the different payload formats of the RFID and
 * fingerprint terminals behind IAttendanceReader, so AttendanceTracker never
 * sees device-specific text.
 */
public class BiometricDeviceAdapter implements IAttendanceReader {

    private static final Pattern RFID_PAYLOAD = Pattern.compile("[0-9A-Fa-f]{8,16}");
    private static final Pattern BIOMETRIC_PAYLOAD = Pattern.compile("FP-(\\d{1,10})");
    private static final int MAX_DEVICE_ID_LENGTH = 30;

    @Override
    public ScanEvent read(RawScan raw) {
        if (raw == null) {
            throw new ValidationException("Scan must not be null");
        }
        if (raw.deviceType() == null) {
            throw new ValidationException("Device type is required");
        }
        if (raw.timestamp() == null) {
            throw new ValidationException("Scan timestamp is required");
        }
        String deviceId = raw.deviceId() == null ? "" : raw.deviceId().trim();
        if (deviceId.isEmpty() || deviceId.length() > MAX_DEVICE_ID_LENGTH) {
            throw new ValidationException("Device id must be 1-" + MAX_DEVICE_ID_LENGTH + " characters");
        }
        String payload = raw.payload() == null ? "" : raw.payload().trim();

        String credentialId = switch (raw.deviceType()) {
            case RFID -> {
                if (!RFID_PAYLOAD.matcher(payload).matches()) {
                    throw new ValidationException("RFID payload must be 8-16 hex characters");
                }
                yield "RFID:" + payload.toUpperCase(Locale.ROOT);
            }
            case BIOMETRIC -> {
                var m = BIOMETRIC_PAYLOAD.matcher(payload);
                if (!m.matches()) {
                    throw new ValidationException("Biometric payload must look like FP-1023");
                }
                yield "BIO:" + m.group(1);
            }
        };
        return new ScanEvent(credentialId, deviceId, raw.timestamp());
    }
}
