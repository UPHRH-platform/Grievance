package org.upsmf.grievance.exception;

import org.upsmf.grievance.util.ErrorCode;

public class InvalidDataException extends CustomException {

    public InvalidDataException(String message) {
        super(message);
    }

    public InvalidDataException(String message, String description) {
        super(message, description);
    }

    public InvalidDataException(String message, ErrorCode errorCode) {
        super(message, errorCode);
    }


    public InvalidDataException(String message, ErrorCode errorCode, String description) {
        super(message, errorCode, description);
    }
}
