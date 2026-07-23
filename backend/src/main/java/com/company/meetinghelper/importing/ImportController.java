package com.company.meetinghelper.importing;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api")
public class ImportController {
    private final ImportService importService;

    public ImportController(ImportService importService) {
        this.importService = importService;
    }

    @GetMapping("/import-templates")
    List<ImportModels.TemplateDescriptor> templates() {
        return importService.templates();
    }

    @GetMapping("/import-templates/{templateCode}/file")
    ResponseEntity<byte[]> templateFile(@PathVariable String templateCode) {
        var bytes = importService.templateFile(templateCode);
        var disposition = ContentDisposition.attachment()
                .filename(templateCode + ".xlsx", StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(bytes);
    }

    @PostMapping(
            value = "/meetings/{meetingId}/imports/preview",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    ImportModels.ImportPreview preview(
            @PathVariable String meetingId,
            @RequestParam String templateCode,
            @RequestPart MultipartFile file
    ) {
        return importService.preview(meetingId, templateCode, file);
    }

    @PostMapping("/meetings/{meetingId}/imports/{token}/commit")
    ImportModels.CommitResult commit(
            @PathVariable String meetingId,
            @PathVariable String token,
            @RequestBody ImportModels.CommitRequest request
    ) {
        return importService.commit(meetingId, token, request);
    }
}

