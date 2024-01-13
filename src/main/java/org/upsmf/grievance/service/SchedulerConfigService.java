package org.upsmf.grievance.service;

import org.upsmf.grievance.dto.MailConfigDto;
import org.upsmf.grievance.dto.SearchMailConfigDto;
import org.upsmf.grievance.dto.SearchMailConfigResponseDto;

import java.util.List;

public interface SchedulerConfigService {

    SearchMailConfigResponseDto searchMailConfig(SearchMailConfigDto searchMailConfigDto);

    MailConfigDto save(MailConfigDto mailConfigDto);

    MailConfigDto update(MailConfigDto mailConfigDto);

    MailConfigDto activateConfigById(Long id, Boolean active, Long userId);

    List<MailConfigDto> getAll();
}
