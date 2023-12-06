package org.upsmf.grievance.service;

import org.upsmf.grievance.dto.MailConfigDto;

import java.util.List;

public interface SchedulerConfigService {

    List<MailConfigDto> getAllMailConfig();

    MailConfigDto save(MailConfigDto mailConfigDto);

    MailConfigDto update(MailConfigDto mailConfigDto);

    MailConfigDto activateConfigById(Long id);

    MailConfigDto deactivateConfigById(Long id);
}
