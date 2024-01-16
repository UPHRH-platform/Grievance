package org.upsmf.grievance.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.upsmf.grievance.exception.DataUnavailabilityException;
import org.upsmf.grievance.model.OtpRequest;
import org.upsmf.grievance.model.RedisTicketData;
import org.upsmf.grievance.service.EmailService;
import org.upsmf.grievance.service.IntegrationService;
import org.upsmf.grievance.service.OtpService;
import org.upsmf.grievance.util.ErrorCode;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Service(value = "OtpService")
@Slf4j
public class OtpServiceImpl implements OtpService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private JavaMailSender mailSender;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private IntegrationService integrationService;

    @Autowired
    private EmailService emailService;

    @Value("${otp.expiration.minutes}")
    private int otpExpirationMinutes;

    @Value("${otp.email.subject}")
    private String otpEmailSubject;

    @Autowired
    public OtpServiceImpl(StringRedisTemplate redisTemplate, JavaMailSender mailSender) {
        this.redisTemplate = redisTemplate;
        this.mailSender = mailSender;
    }

    @Override
    public String generateAndSendOtp(OtpRequest otpRequest) {
        String email = otpRequest.getEmail();
        String otp = generateOtp();
        String mobileOtp= generateOtp();
        log.info("generate mail OTP | mobile OTP - {} || email OTP - {} || OTP request - {}", mobileOtp, otp, otpRequest);
        RedisTicketData redisTicketData = new RedisTicketData();
        redisTicketData.setEmail(email);
        redisTicketData.setPhoneNumber(otpRequest.getPhone());
        redisTicketData.setEmailOtp(otp);
        redisTicketData.setMobileOtp(mobileOtp);

        log.info("generate mail OTP | mobile OTP - {} || email OTP - {} || OTP POJO - {}", mobileOtp, otp, redisTicketData);

        String redisKey = "otp:" + email;
        log.info("generate mail OTP | Redis key - {} || OTP pojo - {}", redisKey, redisTicketData);
        redisTemplate.opsForValue().set(redisKey, toJson(redisTicketData), otpExpirationMinutes, TimeUnit.MINUTES);

        sendOtpEmail(email, otp, otpRequest.getName());
        return otp;
    }

    public Boolean generateAndSendMobileOtp(OtpRequest otpRequest) {
        return integrationService.sendMobileOTP(otpRequest.getName(), otpRequest.getPhone(), generateOtp(otpRequest.getPhone()));
    }

    private String generateOtp(String phoneNumber) {
        String otp = String.valueOf(ThreadLocalRandom.current().nextInt(100000, 999999));
        log.info("generate Mobile OTP | OTP - {} || OTP request - {}", otp, phoneNumber);
        redisTemplate.opsForValue().set(phoneNumber, otp, otpExpirationMinutes, TimeUnit.MINUTES);

        return otp;
    }

    public boolean validateMobileOtp(String phoneNumber, String enteredOtp) {
        log.info("validate Mobile OTP | entered OTP - {} || OTP request - {}", enteredOtp, phoneNumber);
        String redisData = redisTemplate.opsForValue().get(phoneNumber);

        log.info("validate Mobile OTP | redis data - {} || OTP request - {}", redisData, phoneNumber);
        if (redisData == null) {
            throw new DataUnavailabilityException("Unable find OTP data", ErrorCode.DATA_001,
                    "Unable to find OTP against mobile no in redis server");
        }

        if (redisData.equals(enteredOtp)) {
            redisTemplate.delete(redisData);
            return true;
        }

        return false;
    }

    @Override
    public boolean validateOtp(String email, String enteredOtp) {
        String redisKey = "otp:" + email;
        log.info("OTP validation| OTP - {} || mail - {} || Redis key - {}", enteredOtp, email, redisKey);
        String redisData = redisTemplate.opsForValue().get(redisKey);
        log.info("OTP validation| OTP - {} || mail - {} || Redis object - {}", enteredOtp, email, redisData);
        if (redisData != null) {
            RedisTicketData ticketData = fromJson(redisData, RedisTicketData.class);
            log.info("OTP validation| OTP - {} || mail - {} || parsed Redis object - {}", enteredOtp, email, ticketData);
            if (ticketData.getEmailOtp().equals(enteredOtp)) {
                // Remove the data from Redis after successful validation
                redisTemplate.delete(redisKey);
                return true;
            }
        }
        return false;
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            // Handle the exception
            return null;
        }
    }

    private <T> T fromJson(String json, Class<T> valueType) {
        try {
            return objectMapper.readValue(json, valueType);
        } catch (JsonProcessingException e) {
            // Handle the exception
            return null;
        }
    }


    private String generateOtp() {
        int otpLength = 6;
        StringBuilder otp = new StringBuilder();

        for (int i = 0; i < otpLength; i++) {
            int digit = (int) (Math.random() * 10);
            otp.append(digit);
        }

        return otp.toString();
    }

    private void sendOtpEmail(String email, String otp, String name) {
        emailService.sendCreateTicketOTPMail(email, otp, name, otpEmailSubject, otpExpirationMinutes);
    }

    @Override
    public void sendGenericEmail(String email, String subject, String mailBody) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(email);
        message.setSubject(subject);
        message.setText(mailBody);
        mailSender.send(message);
    }
}
