package org.upsmf.grievance.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.upsmf.grievance.dto.MailConfigDto;
import org.upsmf.grievance.dto.SearchMailConfigDto;
import org.upsmf.grievance.dto.SearchMailConfigResponseDto;
import org.upsmf.grievance.exception.SchedulerConfigException;
import org.upsmf.grievance.exception.runtime.InvalidRequestException;
import org.upsmf.grievance.model.MailConfig;
import org.upsmf.grievance.repository.MailConfigRepository;
import org.upsmf.grievance.service.SchedulerConfigService;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
public class SchedulerConfigServiceImpl implements SchedulerConfigService {

    @Autowired
    private MailConfigRepository mailConfigRepository;

    @Override
    public SearchMailConfigResponseDto searchMailConfig(SearchMailConfigDto searchMailConfigDto) {
        return null;
    }

    @Override
    public MailConfigDto save(MailConfigDto mailConfigDto) {
        //validation
        validateCreatePayload(mailConfigDto);
        MailConfig mailConfig = new MailConfig(mailConfigDto);
        mailConfig.setCreatedDate(Timestamp.valueOf(LocalDateTime.now()));
        mailConfig.setUpdatedDate(Timestamp.valueOf(LocalDateTime.now()));
        mailConfig = mailConfigRepository.save(mailConfig);
        // TODO stop and restart scheduledTaskExecutor
        return new MailConfigDto(mailConfig);
    }

    private void validateCreatePayload(MailConfigDto mailConfigDto) {
        if(mailConfigDto == null) {
            throw new InvalidRequestException("Invalid Request");
        }
        if(mailConfigDto.getId() != null && mailConfigDto.getId() > 0) {
            throw new InvalidRequestException("Invalid Request");
        }
        if(mailConfigDto.getAuthorityEmails() == null || mailConfigDto.getAuthorityEmails().isEmpty()) {
            throw new InvalidRequestException("Missing Authority Emails");
        }
        if(mailConfigDto.getAuthorityTitle() == null || mailConfigDto.getAuthorityTitle().isEmpty()) {
            throw new InvalidRequestException("Missing Authority Title");
        }
        if(mailConfigDto.getConfigValue() == null || mailConfigDto.getConfigValue() <= 0) {
            throw new InvalidRequestException("Invalid Configuration value");
        }
        if(mailConfigDto.getCreatedBy() == null || mailConfigDto.getCreatedBy() <= 0) {
            throw new InvalidRequestException("Missing user details");
        }
        if(mailConfigDto.getUpdatedBy() == null || mailConfigDto.getUpdatedBy() <= 0) {
            throw new InvalidRequestException("Missing user details");
        }
    }

    @Override
    public MailConfigDto update(MailConfigDto mailConfigDto) {
        // validation
        validateUpdatePayload(mailConfigDto);
        // find by ID
        Optional<MailConfig> configById = mailConfigRepository.findById(mailConfigDto.getId());
        if(configById.isPresent()) {
            MailConfig existingConfig = configById.get();
            existingConfig.setConfigValue(mailConfigDto.getConfigValue());
            existingConfig.setUpdatedBy(mailConfigDto.getUpdatedBy());
            existingConfig.setUpdatedDate(Timestamp.valueOf(LocalDateTime.now()));
            existingConfig = mailConfigRepository.save(existingConfig);
            // TODO stop and restart scheduledTaskExecutor
            return new MailConfigDto(existingConfig);
        }
        throw new SchedulerConfigException("Unable to update configuration");
    }

    private void validateUpdatePayload(MailConfigDto mailConfigDto) {
        if(mailConfigDto == null) {
            throw new InvalidRequestException("Invalid Request");
        }
        if(mailConfigDto.getId() == null || mailConfigDto.getId() <= 0) {
            throw new InvalidRequestException("Invalid Request");
        }
        if(mailConfigDto.getAuthorityEmails() != null && mailConfigDto.getAuthorityEmails().isEmpty()) {
            throw new InvalidRequestException("Missing Authority Emails");
        }
        if(mailConfigDto.getConfigValue() == null || mailConfigDto.getConfigValue() <= 0) {
            throw new InvalidRequestException("Invalid Configuration value");
        }
        if(mailConfigDto.getUpdatedBy() == null || mailConfigDto.getUpdatedBy() <= 0) {
            throw new InvalidRequestException("Missing user details");
        }
    }

    @Override
    public MailConfigDto activateConfigById(Long id, Boolean active, Long userId) {
        if(id == null || id <= 0) {
            throw new InvalidRequestException("Invalid request");
        }
        if(userId == null || userId <= 0) {
            throw new InvalidRequestException("Invalid request");
        }
        if(active == null) {
            active = true;
        }
        Optional<MailConfig> configById = mailConfigRepository.findById(id);
        if(configById.isPresent()) {
            MailConfig existingConfig = configById.get();
            existingConfig.setActive(active.booleanValue());
            existingConfig.setUpdatedBy(userId);
            existingConfig.setUpdatedDate(Timestamp.valueOf(LocalDateTime.now()));
            existingConfig = mailConfigRepository.save(existingConfig);
            // TODO start scheduledTaskExecutor
            return new MailConfigDto(existingConfig);
        }
        throw new SchedulerConfigException("Unable to activate configuration");
    }

    @Override
    public List<MailConfigDto> getAll() {
        Iterable<MailConfig> configIterable = mailConfigRepository.findAll();
        List<MailConfigDto> configs = new ArrayList<>();
        Iterator<MailConfig> iterator = configIterable.iterator();
        while (iterator.hasNext()) {
            MailConfig mailConfig = iterator.next();
            configs.add(new MailConfigDto(mailConfig));
        }
        return configs;
    }
}
