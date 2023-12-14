package org.upsmf.grievance.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.ToString;
import org.apache.commons.lang.ArrayUtils;
import org.upsmf.grievance.model.MailConfig;

import java.sql.Timestamp;
import java.util.Arrays;
import java.util.List;

@Data
@AllArgsConstructor
@ToString
@Builder
public class MailConfigDto {

    private Long id;
    private String authorityTitle;
    private List<String> authorityEmails;
    private Long configValue;
    private boolean active = true;
    private Long createdBy;
    private Timestamp createdDate;
    private Long updatedBy;
    private Timestamp updatedDate;

    public MailConfigDto(MailConfig mailConfig) {
        this.id = mailConfig.getId();
        this.authorityTitle = mailConfig.getAuthorityTitle();
        if(mailConfig.getAuthorityEmails() != null && !mailConfig.getAuthorityEmails().isBlank()) {
            this.authorityEmails = Arrays.asList(mailConfig.getAuthorityEmails());
        } else {
            this.authorityEmails = Arrays.asList(ArrayUtils.EMPTY_STRING_ARRAY);
        }
        this.configValue = mailConfig.getConfigValue();
        this.active = mailConfig.isActive();
        this.createdBy = mailConfig.getCreatedBy();
        this.createdDate = mailConfig.getCreatedDate();
        this.updatedBy = mailConfig.getUpdatedBy();
        this.updatedDate = mailConfig.getUpdatedDate();
    }
}
