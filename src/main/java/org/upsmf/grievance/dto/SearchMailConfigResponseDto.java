package org.upsmf.grievance.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.ToString;

import java.util.List;

@Data
@AllArgsConstructor
@ToString
@Builder
public class SearchMailConfigResponseDto {

    private Long total;

    private List<MailConfigDto> mailConfigs;
}
