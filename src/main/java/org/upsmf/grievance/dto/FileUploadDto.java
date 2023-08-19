package org.upsmf.grievance.dto;

import lombok.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class FileUploadDto {

    private String fileName;
    private String fileExtension;
    private Long uploadedBy;
}
