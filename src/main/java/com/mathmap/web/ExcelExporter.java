package com.mathmap.web;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import com.mathmap.game.Room.ScoreSheet;

/**
 * 점수표를 엑셀(.xlsx) 파일로 만든다.
 * "점수" 시트: 학급번호, 이름, 총점, 1번(3점), 2번(5점) ... (O/X/-)
 * "문제" 시트: 번호, 상태, 형식, 문제, 보기, 정답, 배점, 제출, 정답, 정답률
 */
final class ExcelExporter {

    private ExcelExporter() {}

    static byte[] write(ScoreSheet sheetData, ByteArrayOutputStream out) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("점수");
            CellStyle bold = wb.createCellStyle();
            Font font = wb.createFont();
            font.setBold(true);
            bold.setFont(font);

            Row header = sheet.createRow(0);
            String[] fixed = {"학급번호", "이름", "총점"};
            int col = 0;
            for (String h : fixed) {
                header.createCell(col).setCellValue(h);
                header.getCell(col++).setCellStyle(bold);
            }
            for (String h : sheetData.questionHeaders()) {
                header.createCell(col).setCellValue(h);
                header.getCell(col++).setCellStyle(bold);
            }

            int r = 1;
            for (ScoreSheet.Row row : sheetData.rows()) {
                Row x = sheet.createRow(r++);
                // 엑셀이 수식으로 해석하지 않도록 문자열은 그대로 텍스트 셀로 저장
                x.createCell(0).setCellValue(row.classNo());
                x.createCell(1).setCellValue(row.name());
                x.createCell(2).setCellValue(row.score());
                int c = 3;
                for (String m : row.marks()) {
                    x.createCell(c++).setCellValue(m);
                }
            }
            for (int i = 0; i < col; i++) {
                sheet.setColumnWidth(i, i == 1 ? 14 * 256 : 10 * 256);
            }
            sheet.createFreezePane(3, 1);

            writeQuestions(wb, sheetData, bold);
            wb.write(out);
        }
        return out.toByteArray();
    }

    private static void writeQuestions(XSSFWorkbook wb, ScoreSheet data, CellStyle bold) {
        Sheet sheet = wb.createSheet("문제");
        CellStyle wrap = wb.createCellStyle();
        wrap.setWrapText(true);
        wrap.setVerticalAlignment(VerticalAlignment.TOP);
        CellStyle top = wb.createCellStyle();
        top.setVerticalAlignment(VerticalAlignment.TOP);

        String[] headers = {"번호", "상태", "형식", "문제", "보기", "정답", "배점", "제출", "정답", "정답률"};
        Row header = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            header.createCell(i).setCellValue(headers[i]);
            header.getCell(i).setCellStyle(bold);
        }
        int r = 1;
        for (ScoreSheet.QuestionRow q : data.questions()) {
            Row x = sheet.createRow(r++);
            x.createCell(0).setCellValue(q.number());
            x.createCell(1).setCellValue(q.status());
            x.createCell(2).setCellValue(q.kind());
            x.createCell(3).setCellValue(MathText.toPlain(q.content()));
            x.createCell(4).setCellValue(MathText.toPlain(q.choices()));
            x.createCell(5).setCellValue(MathText.toPlain(q.answer()));
            x.createCell(6).setCellValue(q.points());
            x.createCell(7).setCellValue(q.answered() + "/" + q.studentCount());
            x.createCell(8).setCellValue(q.correct());
            x.createCell(9).setCellValue(q.answered() == 0 ? "-" : Math.round(q.correct() * 100.0 / q.answered()) + "%");
            for (int c = 0; c < headers.length; c++) {
                x.getCell(c).setCellStyle(c == 3 || c == 4 || c == 5 ? wrap : top);
            }
        }
        int[] widths = {6, 9, 8, 50, 30, 20, 6, 8, 6, 8};
        for (int i = 0; i < widths.length; i++) {
            sheet.setColumnWidth(i, widths[i] * 256);
        }
        sheet.createFreezePane(0, 1);
    }
}
