package org.upsmf.grievance.service;

import org.upsmf.grievance.dto.MailConfigDto;
import org.upsmf.grievance.dto.SearchMailConfigDto;
import org.upsmf.grievance.dto.SearchMailConfigResponseDto;

public interface SchedulerConfigService {

    SearchMailConfigResponseDto searchMailConfig(SearchMailConfigDto searchMailConfigDto);

    MailConfigDto save(MailConfigDto mailConfigDto);

    MailConfigDto update(MailConfigDto mailConfigDto);

    MailConfigDto activateConfigById(Long id, Long userId);

    MailConfigDto deactivateConfigById(Long id, Long userId);
}
