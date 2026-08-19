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
    /** 最大页数（防超大 PDF 拖垮解析） */
    private static final int MAX_PAGES = 50;

    /** 最大提取字符数（截断，防内存膨胀） */
    private static final int MAX_CHARS = 50000;

    public static String extractText(File pdfFile) {
        try (PDDocument document = Loader.loadPDF(pdfFile)) {
            if (document.getNumberOfPages() > MAX_PAGES) {
                throw new RuntimeException("PDF 页数超过上限（" + MAX_PAGES + " 页），请精简后重试");
            }
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);
            if (text != null && text.length() > MAX_CHARS) {
                text = text.substring(0, MAX_CHARS);
            }
            return text;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("PDF文本提取失败: " + e.getMessage());
        }
    }
}
