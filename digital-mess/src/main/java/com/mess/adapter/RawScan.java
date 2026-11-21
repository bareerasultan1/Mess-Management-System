package com.mess.adapter;

import java.time.Instant;

/** What a terminal sends us, before any cleaning. */
public record RawScan(DeviceType deviceType, String payload, String deviceId, Instant timestamp) { }
