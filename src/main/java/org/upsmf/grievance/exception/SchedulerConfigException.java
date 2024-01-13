package org.upsmf.grievance.exception;

import org.upsmf.grievance.util.ErrorCode;

public class SchedulerConfigException extends CustomException {
    public SchedulerConfigException(String message) {
        super(message);
    }

    public SchedulerConfigException(String message, String description) {
        super(message, description);
    }

    public SchedulerConfigException(String message, ErrorCode errorCode) {
        super(message, errorCode);
    }

    public SchedulerConfigException(String message, ErrorCode errorCode, String description) {
        super(message, errorCode, description);
    }
}
