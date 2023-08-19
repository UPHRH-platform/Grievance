package org.upsmf.grievance.service.impl;

import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.storage.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.upsmf.grievance.dto.FileUploadDto;
import org.upsmf.grievance.service.AttachmentService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
@Slf4j
public class AttachmentServiceImpl implements AttachmentService {

    @Value("${gcp.config.file.path}")
    private String gcpConfigFilePath;

    @Value("${gcp.bucket.name}")
    private String gcpBucketName;

    @Value("${gcp.bucket.folder.name}")
    private String gcpFolderName;

    @Value("${gcp.max.file.size}")
    private String gcpMaxFileSize;

    @Value("${gcp.project.id}")
    private String gcpProjectId;

    @Value("${gcp.client.id}")
    private String gcpClientId;

    @Value("${gcp.client.email}")
    private String gcpClientEmail;

    @Value("${gcp.pkcs.key}")
    private String gcpPkcsKey;

    @Value("${gcp.private.key.id}")
    private String gcpPrivateKeyId;

    @Override
    public void uploadObject(MultipartFile file, FileUploadDto fileUploadDto) {
        try {
            Path filePath = Files.createTempFile(fileUploadDto.getFileName().split(".")[0], fileUploadDto.getFileName().split(".")[1]);
            ServiceAccountCredentials credentials = ServiceAccountCredentials.fromPkcs8(gcpClientId, gcpClientEmail,
                    gcpPkcsKey, gcpPrivateKeyId, new java.util.ArrayList<String>());
            System.out.println("credentials created");
            Storage storage = StorageOptions.newBuilder().setProjectId(gcpProjectId).setCredentials(credentials).build().getService();
            System.out.println("storage object created");
            BlobId blobId = BlobId.of(gcpBucketName, fileUploadDto.getFileName());
            BlobInfo blobInfo = BlobInfo.newBuilder(blobId).build();
            Blob blob = storage.createFrom(blobInfo, filePath);
            // TODO return correct response after testing
            System.out.println(blob);
        } catch (IOException e) {
            log.error("Error while uploading attachment", e);
        }
    }

}
