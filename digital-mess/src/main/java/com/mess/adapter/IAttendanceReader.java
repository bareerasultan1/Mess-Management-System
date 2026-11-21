package com.mess.adapter;

public interface IAttendanceReader {

    /** Converts a raw device scan into a ScanEvent, or throws ValidationException if it is malformed. */
    ScanEvent read(RawScan raw);
}
