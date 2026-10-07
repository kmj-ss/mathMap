package com.mathmap.web;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import com.mathmap.game.GameService.ScoreSheet;

/** 점수표를 엑셀(.xlsx) 파일로 만든다. 열: 학급번호, 이름, 총점, 1번, 2번 ... (O/X/-) */
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
            wb.write(out);
        }
        return out.toByteArray();
    }
}
