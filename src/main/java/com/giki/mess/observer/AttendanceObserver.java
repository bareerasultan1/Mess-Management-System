package com.giki.mess.observer;

/** OBSERVER pattern: anything that wants to react to an accepted scan implements this. */
public interface AttendanceObserver {

    void onAttendanceRecorded(ScanEvent event);
}
