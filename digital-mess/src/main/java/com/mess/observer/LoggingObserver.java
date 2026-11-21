package com.mess.observer;

import java.util.logging.Logger;

/** Simple observer used by the demo; billing and alert observers will belong to the other modules. */
public class LoggingObserver implements AttendanceObserver {

    private static final Logger LOG = Logger.getLogger(LoggingObserver.class.getName());

    @Override
    public void onAttendanceRecorded(AttendanceRecordedEvent e) {
        LOG.info(() -> "Attendance recorded: " + e.studentName() + " " + e.scanType()
                + " " + e.mealType() + " at " + e.at());
    }
}
