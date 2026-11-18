package com.mess.adapter;

import java.time.Instant;

/** Device-independent scan used by the domain. credentialId looks like RFID:04A3B2C1 or BIO:1023. */
public record ScanEvent(String credentialId, String deviceId, Instant scannedAt) { }
