package com.giki.mess.util;

/** Hides personal data before it is written to logs (requirement QA-05). */
public final class LogRedactor {

    private LogRedactor() {
    }

    /** "2023294" becomes "202****": keeps the intake prefix, hides the individual part. */
    public static String maskStudentId(String studentId) {
        if (studentId == null || studentId.isBlank()) {
            return "****";
        }
        int visible = Math.min(3, studentId.length() - 1);
        return studentId.substring(0, Math.max(visible, 0)) + "****";
    }
}
