package com.fakemianshi.util;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.File;

/**
 * PDF 工具类：基于 pdfbox 3.x 提取 PDF 文本。
 */
public final class PdfUtil {

    private PdfUtil() {
    }

    /**
     * 提取 PDF 文件中的纯文本内容。
     *
     * @param pdfFile PDF 文件
     * @return 提取出的文本
     * @throws RuntimeException 提取失败时抛出
     */
    public static String extractText(File pdfFile) {
        try (PDDocument document = Loader.loadPDF(pdfFile)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        } catch (Exception e) {
            throw new RuntimeException("PDF文本提取失败: " + e.getMessage());
        }
    }
}
