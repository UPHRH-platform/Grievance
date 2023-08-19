package org.upsmf.grievance.service;

import org.springframework.web.multipart.MultipartFile;
import org.upsmf.grievance.dto.FileUploadDto;

public interface AttachmentService {

    void uploadObject(MultipartFile file, FileUploadDto fileUploadDto);
}
