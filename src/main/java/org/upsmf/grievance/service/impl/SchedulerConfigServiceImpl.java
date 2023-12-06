package org.upsmf.grievance.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.upsmf.grievance.dto.MailConfigDto;
import org.upsmf.grievance.dto.SearchMailConfigDto;
import org.upsmf.grievance.dto.SearchMailConfigResponseDto;
import org.upsmf.grievance.repository.MailConfigRepository;
import org.upsmf.grievance.service.SchedulerConfigService;

import java.util.List;

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
        return null;
    }

    @Override
    public MailConfigDto update(MailConfigDto mailConfigDto) {
        return null;
    }

    @Override
    public MailConfigDto activateConfigById(Long id) {
        return null;
    }

    @Override
    public MailConfigDto deactivateConfigById(Long id) {
        return null;
    }
}
