package org.upsmf.grievance.exception;

import org.upsmf.grievance.util.ErrorCode;

public class MailConfigException extends CustomException {
    public MailConfigException(String message) {
        super(message);
    }

    public MailConfigException(String message, String description) {
        super(message, description);
    }

    public MailConfigException(String message, ErrorCode errorCode) {
        super(message, errorCode);
    }

    public MailConfigException(String message, ErrorCode errorCode, String description) {
        super(message, errorCode, description);
    }
}
