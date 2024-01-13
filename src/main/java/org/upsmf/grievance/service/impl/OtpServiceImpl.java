package org.upsmf.grievance.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
        RedisTicketData redisTicketData = new RedisTicketData();
        redisTicketData.setEmail(email);
        redisTicketData.setPhoneNumber(otpRequest.getPhone());
        redisTicketData.setEmailOtp(otp);
        redisTicketData.setMobileOtp(mobileOtp);

        String redisKey = "otp:" + email;
        redisTemplate.opsForValue().set(redisKey, toJson(redisTicketData), otpExpirationMinutes, TimeUnit.MINUTES);

        sendOtpEmail(email, otp, otpRequest.getName());
        return otp;
    }

    public Boolean generateAndSendMobileOtp(OtpRequest otpRequest) {
        return integrationService.sendMobileOTP(otpRequest.getName(), otpRequest.getPhone(), generateOtp(otpRequest.getPhone()));
    }

    private String generateOtp(String phoneNumber) {
        String otp = String.valueOf(ThreadLocalRandom.current().nextInt(100000, 999999));
        redisTemplate.opsForValue().set(phoneNumber, otp, otpExpirationMinutes, TimeUnit.MINUTES);

        return otp;
    }

    public boolean validateMobileOtp(String phoneNumber, String enteredOtp) {
        String redisData = redisTemplate.opsForValue().get(phoneNumber);

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
        String redisData = redisTemplate.opsForValue().get(redisKey);
        if (redisData != null) {
            RedisTicketData ticketData = fromJson(redisData, RedisTicketData.class);
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
