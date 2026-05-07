package cn.hollis.llm.mentor.rag.controller;

import cn.hollis.llm.mentor.rag.fileserver.MinioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;

@RestController
@RequestMapping("/rag/files")
public class RagFileController {

    @Autowired
    private MinioService minioService;

    @GetMapping("/upload")
    public ResponseEntity<String> uploadFile(@RequestParam("fileUrl") String fileUrl) {
        try {
            // 从URL下载文件并转换为MutipartFile
            URL url = new URL(fileUrl);
            URLConnection connection = url.openConnection();
            InputStream inputStream = connection.getInputStream();

            // 从URL中提取文件名
            String fileName = fileUrl.substring(fileUrl.lastIndexOf('/') + 1);
            if (fileName.isEmpty() || !fileName.contains(".")) {
                fileName = "file_" + System.currentTimeMillis();
            }

            // 获取文件内容类型
            String contentType = connection.getContentType();
            if (contentType == null) {
                contentType = "application/octet-stream";
            }

            // 将输入流转换为字节数组
            byte[] fileContent = inputStream.readAllBytes();
            inputStream.close();

            String objectName = System.currentTimeMillis() + "_" + fileName;
            minioService.uploadFile(objectName, fileContent, contentType);
            return ResponseEntity.ok("上传成功: " + objectName);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("上传失败: " + e.getMessage());
        }
    }

    @GetMapping("/download-url/{objectName}")
    public ResponseEntity<String> getDownloadUrl(@PathVariable String objectName) {
        try {
            String url = minioService.getPresignedUrl(objectName);
            return ResponseEntity.ok(url);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("生成下载链接失败");
        }
    }
}
