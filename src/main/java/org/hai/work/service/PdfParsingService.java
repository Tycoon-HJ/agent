package org.hai.work.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Base64;

/**
 * PDF 文件解析服务
 * <p>
 * 使用 Apache PDFBox 提取 PDF 中的文本内容。
 * 支持 base64 编码的 PDF 数据输入。
 */
@Slf4j
@Service
public class PdfParsingService {

    private static final int MAX_PAGES = 100;
    private static final int MAX_TEXT_LENGTH = 100_000;

    /**
     * 从 base64 编码的 PDF 数据中提取文本
     *
     * @param base64Data base64 编码的 PDF 数据（可带 data:application/pdf;base64, 前缀）
     * @return 提取的文本内容
     * @throws IOException 当 PDF 解析失败时
     */
    public String extractText(String base64Data) throws IOException {
        if (base64Data == null || base64Data.isBlank()) {
            throw new IllegalArgumentException("PDF 数据不能为空");
        }

        // 去掉 data URI 前缀
        String raw = base64Data.trim();
        if (raw.startsWith("data:")) {
            int commaIdx = raw.indexOf(',');
            if (commaIdx >= 0) {
                raw = raw.substring(commaIdx + 1);
            }
        }

        byte[] pdfBytes = Base64.getDecoder().decode(raw);
        log.info("PDF 解析开始，数据大小: {} bytes", pdfBytes.length);

        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            int pageCount = document.getNumberOfPages();
            log.info("PDF 页数: {}", pageCount);

            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);

            // 限制解析页数
            if (pageCount > MAX_PAGES) {
                stripper.setEndPage(MAX_PAGES);
                log.warn("PDF 页数超过限制，只解析前 {} 页", MAX_PAGES);
            }

            String text = stripper.getText(document);

            // 截断过长文本
            if (text.length() > MAX_TEXT_LENGTH) {
                text = text.substring(0, MAX_TEXT_LENGTH) + "\n\n[文本已截断，总长度: " + text.length() + " 字符]";
                log.warn("PDF 文本超过长度限制，已截断到 {} 字符", MAX_TEXT_LENGTH);
            }

            log.info("PDF 解析完成，提取文本长度: {}", text.length());
            return text;
        }
    }
}
