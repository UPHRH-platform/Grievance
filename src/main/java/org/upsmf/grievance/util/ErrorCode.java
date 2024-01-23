package org.upsmf.grievance.util;

public enum ErrorCode {

    OTP_001("Failed OTP - calling external resource to send otp"),
    OTP_002("Invalid sender id - calling external resource to send otp"),
    OTP_003("Invalid channel - calling external resource to send otp"),
    OTP_004("OTP server error - calling external resource to send otp"),
    OTP_005("OTP mismatch"),
    DATA_001("Data unavailability in Redis server"),
    USER_001("Failed to find user"),
    USER_002("User already exist"),
    USER_003("Failed User creation"),
    TKT_001("Failed ticket creation"),
    TKT_002("Failed ticket creation - internal server error"),
    OTP_000("Internal OTP error"),
    MAIL_001("Failed to save configuration."),
    MAIL_002("Failed to update configuration."),
    MAIL_003("Failed to fetch configurations."),
    MAIL_004("Failed to activate configuration."),
    MAIL_005("Failed to deactivate configuration."),
    CONFIG_001("Invalid Request"),
    CONFIG_002("Error in updating request");

    private String description;

    ErrorCode(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
