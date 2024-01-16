package org.upsmf.grievance.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.storage.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.upsmf.grievance.service.AttachmentService;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.concurrent.TimeUnit;


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

    @Value("${gcp.sub.folder.path}")
    private String subFolderPath;

    @Autowired
    private ObjectMapper mapper;

    @Override
    public ResponseEntity<String> uploadObject(MultipartFile file) {
        Path filePath = null;
        try {
            // validate file
            String fileName = file.getOriginalFilename();
            filePath = Files.createTempFile(fileName.split("\\.")[0], fileName.split("\\.")[1]);
            file.transferTo(filePath);
            validateFile(filePath);
            // create credentials
            ServiceAccountCredentials credentials = ServiceAccountCredentials.fromPkcs8(gcpClientId, gcpClientEmail,
                    gcpPkcsKey, gcpPrivateKeyId, new ArrayList<String>());
            log.info("credentials created");
            Storage storage = StorageOptions.newBuilder().setProjectId(gcpProjectId).setCredentials(credentials).build().getService();
            log.info("storage object created");
            String gcpFileName = gcpFolderName+"/"+Calendar.getInstance().getTimeInMillis()+"_"+fileName;

            byte[] fileData = FileUtils.readFileToByteArray(convertFile(file));
            String contentType = Files.probeContentType(new File(file.getOriginalFilename()).toPath());

//            BlobId blobId = BlobId.of(gcpBucketName, gcpFileName);
//            BlobInfo blobInfo = BlobInfo.newBuilder(blobId).build();
//            Blob blob = storage.create(blobInfo, new FileInputStream(filePath.toFile()));

            Bucket bucket = storage.get(gcpBucketName,Storage.BucketGetOption.fields());
            Blob blob = bucket.create(gcpFileName, fileData, contentType);

            // TODO return correct response after testing
            log.info(blob.toString());
            URL url = blob.signUrl(30, TimeUnit.DAYS);
            log.info("URL - {}", url);
            String urlString = url.toURI().toString();
            ObjectNode urlNode = mapper.createObjectNode();
            urlNode.put("url", urlString);
            urlNode.put("file_name", fileName);
            ObjectNode node = mapper.createObjectNode();
            node.put("result", urlNode);
            return ResponseEntity.ok(mapper.writeValueAsString(node));
        } catch (IOException e) {
            log.error("Error while uploading attachment", e);
            return ResponseEntity.internalServerError().body("Error while uploading file.");
        } catch (URISyntaxException e) {
            log.error("Error converting url ", e);
            return ResponseEntity.internalServerError().body("Error while uploading file.");
        } finally {
            if(filePath != null) {
                try {
                    Files.delete(filePath);
                } catch (IOException e) {
                    log.error("Unable to delete temp file", e);
                }
            }
        }
    }

    private boolean validateFile(Path path) throws IOException {
        if(Files.isExecutable(path)) {
            throw new RuntimeException("Invalid file");
        }
        Tika tika = new Tika();
        String fileExt = tika.detect(path);
        if(fileExt.equalsIgnoreCase("application/pdf")) {
            return true;
        } else if(fileExt.startsWith("image")) {
            return true;
        }
        throw new RuntimeException("Invalid file type. Supported files are PDF and Images.");
    }

    private File convertFile(MultipartFile file) {
        FileOutputStream outputStream = null;
        try{
            if(file.getOriginalFilename() == null){
                throw new RuntimeException("Original file name is null");
            }
            File convertedFile = new File(file.getOriginalFilename());
            outputStream = new FileOutputStream(convertedFile);
            outputStream.write(file.getBytes());
            log.debug("Converting multipart file : {}", convertedFile);
            return convertedFile;
        }catch (Exception e){
            throw new RuntimeException("An error has occurred while converting the file");
        }finally {
            if(outputStream!=null)
                try {
                    outputStream.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
        }

    }
}
