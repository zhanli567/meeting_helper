package com.company.meetinghelper.export.api;

import com.company.meetinghelper.export.api.dto.request.ExportExcelRequest;
import com.company.meetinghelper.export.service.ExportService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

/**
 * ExportController 类。
 */
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
         * excel 方法。
         * @param meetingId meetingId 参数。
         * @param request request 参数。
         * @return 返回结果。
         */
@PostMapping("/excel")
    public ResponseEntity<byte[]> excel(
            @PathVariable String meetingId,
            @RequestBody(required = false) ExportExcelRequest request
    ) {
        ExportExcelRequest normalized = request == null
                ? new ExportExcelRequest(null, null)
                : request;
        return download(
                exportService.exportExcel(meetingId, normalized),
                "meeting-seating.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    }

    private ResponseEntity<byte[]> download(byte[] bytes, String filename, String contentType) {
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(filename, StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.parseMediaType(contentType))
                .body(bytes);
    }
}
