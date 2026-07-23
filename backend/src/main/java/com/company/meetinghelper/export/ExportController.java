package com.company.meetinghelper.export;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/meetings/{meetingId}/exports")
public class ExportController {
    private final ExportService exportService;

    public ExportController(ExportService exportService) {
        this.exportService = exportService;
    }

    @GetMapping("/excel")
    ResponseEntity<byte[]> excel(@PathVariable String meetingId) {
        return download(
                exportService.exportExcel(meetingId),
                "meeting-seating.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    }

    @GetMapping("/pdf")
    ResponseEntity<byte[]> pdf(@PathVariable String meetingId) {
        return download(exportService.exportPdf(meetingId), "meeting-seating.pdf", "application/pdf");
    }

    private ResponseEntity<byte[]> download(byte[] bytes, String filename, String contentType) {
        var disposition = ContentDisposition.attachment()
                .filename(filename, StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.parseMediaType(contentType))
                .body(bytes);
    }
}

