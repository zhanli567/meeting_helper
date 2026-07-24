package com.company.meetinghelper.importing.api;

import com.company.meetinghelper.importing.api.dto.request.CommitRequest;
import com.company.meetinghelper.importing.api.dto.response.CommitResult;
import com.company.meetinghelper.importing.api.dto.response.ImportPreview;
import com.company.meetinghelper.importing.api.dto.response.TemplateDescriptor;
import com.company.meetinghelper.importing.service.ImportService;
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
@RequestMapping
public class ImportController {
    private final ImportService importService;

    /**
     * 创建人员导入接口控制器。
     *
     * @param importService 导入服务
     */
    public ImportController(ImportService importService) {
        this.importService = importService;
    }

    /**
     * 查询可用的人员导入模板。
     *
     * @return 导入模板列表
     */
    @GetMapping("/import-templates")
    public List<TemplateDescriptor> templates() {
        return importService.templates();
    }

    /**
     * 下载指定人员导入模板。
     *
     * @param templateCode 模板编码
     * @return Excel模板文件响应
     */
    @GetMapping("/import-templates/{templateCode}/file")
    public ResponseEntity<byte[]> templateFile(@PathVariable String templateCode) {
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

    /**
     * 解析上传文件并生成导入预览。
     *
     * @param meetingId 会议ID
     * @param templateCode 模板编码
     * @param file 上传的Excel文件
     * @return 导入预览
     */
    @PostMapping(
            value = "/meetings/{meetingId}/imports/preview",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ImportPreview preview(
            @PathVariable String meetingId,
            @RequestParam String templateCode,
            @RequestPart MultipartFile file
    ) {
        return importService.preview(meetingId, templateCode, file);
    }

    /**
     * 确认导入预览中的人员数据。
     *
     * @param meetingId 会议ID
     * @param token 预览令牌
     * @param request 导入确认请求
     * @return 导入结果
     */
    @PostMapping("/meetings/{meetingId}/imports/{token}/commit")
    public CommitResult commit(
            @PathVariable String meetingId,
            @PathVariable String token,
            @RequestBody CommitRequest request
    ) {
        return importService.commit(meetingId, token, request);
    }
}
