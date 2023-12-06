package org.upsmf.grievance.service.impl;

import org.springframework.stereotype.Service;
import org.upsmf.grievance.dto.MailConfigDto;
import org.upsmf.grievance.service.SchedulerConfigService;

import java.util.List;

@Service
public class SchedulerConfigServiceImpl implements SchedulerConfigService {
    @Override
    public List<MailConfigDto> getAllMailConfig() {
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
