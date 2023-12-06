package org.upsmf.grievance.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import org.apache.commons.lang.StringUtils;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.upsmf.grievance.dto.MailConfigDto;

import javax.persistence.*;
import java.sql.Timestamp;
import java.util.List;

@Data
@AllArgsConstructor
@Builder
@Entity
@Table(name = "mail_config")
public class MailConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @Column(name = "authority_title")
    private String authorityTitle;

    @Column(name = "authority_emails")
    private String authorityEmails;

    @Column(name = "config_value")
    private Long configValue;

    @Column(name = "active")
    private boolean active = true;

    @Column(name = "created_by")
    private Long createdBy;

    @CreationTimestamp
    @Column(name = "created_date")
    private Timestamp createdDate;

    @Column(name = "updated_by")
    private Long updatedBy;

    @UpdateTimestamp
    @Column(name = "updated_date")
    private Timestamp updatedDate;

    public MailConfig(MailConfigDto mailConfigDto) {
        this.id = mailConfigDto.getId();
        this.authorityTitle = mailConfigDto.getAuthorityTitle();
        if(mailConfigDto.getAuthorityEmails() != null && !mailConfigDto.getAuthorityEmails().isEmpty()) {
            this.authorityEmails = String.join(",", mailConfigDto.getAuthorityEmails());
        } else {
            this.authorityEmails = StringUtils.EMPTY;
        }
        this.authorityEmails = String.join(",", mailConfigDto.getAuthorityEmails());
        this.configValue = mailConfigDto.getConfigValue();
        this.active = mailConfigDto.isActive();
        this.createdBy = mailConfigDto.getCreatedBy();
        this.createdDate = mailConfigDto.getCreatedDate();
        this.updatedBy = mailConfigDto.getUpdatedBy();
        this.updatedDate = mailConfigDto.getUpdatedDate();
    }
}
