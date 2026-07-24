package com.company.meetinghelper.export.api;

import com.company.meetinghelper.export.service.ExportService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/meetings/{meetingId}/exports")
public class ExportController {
    private final ExportService exportService;

    /**
     * 创建导出接口控制器。
     *
     * @param exportService 导出服务
     */
    public ExportController(ExportService exportService) {
        this.exportService = exportService;
    }

    /**
     * 导出会议排座Excel文件。
     *
     * @param meetingId 会议ID
     * @param versionId 可选的发布版本ID
     * @return Excel文件响应
     */
    @GetMapping("/excel")
    public ResponseEntity<byte[]> excel(
            @PathVariable String meetingId,
            @RequestParam(required = false) String versionId
    ) {
        return download(
                exportService.exportExcel(meetingId, versionId),
                "meeting-seating.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    }

    /**
     * 导出会议排座PDF文件。
     *
     * @param meetingId 会议ID
     * @param versionId 可选的发布版本ID
     * @return PDF文件响应
     */
    @GetMapping("/pdf")
    public ResponseEntity<byte[]> pdf(
            @PathVariable String meetingId,
            @RequestParam(required = false) String versionId
    ) {
        return download(exportService.exportPdf(meetingId, versionId), "meeting-seating.pdf", "application/pdf");
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
