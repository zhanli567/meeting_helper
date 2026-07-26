package com.company.meetinghelper.importing.api;

import com.company.meetinghelper.importing.api.dto.response.CommitResult;
import com.company.meetinghelper.importing.api.dto.response.ImportPreview;
import com.company.meetinghelper.importing.service.ImportService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping
public class ImportController {
    private final ImportService importService;

    /**
     * 创建通用人员导入接口控制器。
     *
     * @param importService 导入服务
     */
    public ImportController(ImportService importService) {
        this.importService = importService;
    }

    /**
     * 下载单一通用人员导入模板。
     *
     * @return Excel模板文件响应
     */
    @GetMapping("/imports/template")
    public ResponseEntity<byte[]> template() {
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename("参会人员导入模板.xlsx", StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                ))
                .body(importService.template());
    }

    /**
     * 解析上传文件并生成导入预览。
     *
     * @param meetingId 会议ID
     * @param file 上传的Excel文件
     * @return 导入预览
     */
    @PostMapping(
            value = "/meetings/{meetingId}/imports/preview",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ImportPreview preview(
            @PathVariable String meetingId,
            @RequestPart MultipartFile file
    ) {
        return importService.preview(meetingId, file);
    }

    /**
     * 提交通用人员导入预览。
     *
     * @param meetingId 会议ID
     * @param token 预览令牌
     * @return 导入统计结果
     */
    @PostMapping("/meetings/{meetingId}/imports/{token}/commit")
    public CommitResult commit(
            @PathVariable String meetingId,
            @PathVariable String token
    ) {
        return importService.commit(meetingId, token);
    }
}
