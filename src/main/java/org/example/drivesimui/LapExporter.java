package org.example.drivesimui;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.util.Matrix;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Exports a list of LapTime records to CSV or PDF.
 *
 * Callers supply the ordered list of column names they want included.
 * Column resolution is fully polymorphic — this class never casts a Vehicle.
 *
 * Fixed columns always exported: Lap #, Lap Time, Split, Car.
 * Setup columns are identified by matching against Vehicle.getColumnHeaders().
 */
public class LapExporter {

    public enum Format { CSV, PDF }

    private static final String[] FIXED_HEADERS = {"Lap", "Lap Time", "Split", "Car"};

    // -----------------------------------------------------------------------
    // Public entry point
    // -----------------------------------------------------------------------

    /**
     * @param laps           ordered list of laps to export
     * @param selectedCols   column names the user chose (from getColumnHeaders())
     * @param filePath       destination file path (without extension — added automatically)
     * @param format         CSV or PDF
     * @throws IOException   if writing fails
     */
    public void export(List<LapTime> laps, List<String> selectedCols,
                       String filePath, Format format) throws IOException {
        String path = filePath.endsWith("." + format.name().toLowerCase())
                ? filePath
                : filePath + "." + format.name().toLowerCase();

        if (format == Format.CSV) {
            exportCsv(laps, selectedCols, path);
        } else {
            exportPdf(laps, selectedCols, path);
        }
    }

    // -----------------------------------------------------------------------
    // CSV
    // -----------------------------------------------------------------------

    private void exportCsv(List<LapTime> laps, List<String> selectedCols,
                            String path) throws IOException {
        try (PrintWriter w = new PrintWriter(new FileWriter(path))) {
            w.println(buildHeaderLine(selectedCols));
            int lapNum = 1;
            for (LapTime lap : laps) {
                w.println(buildDataLine(lap, selectedCols, lapNum++));
            }
        }
    }

    private String buildHeaderLine(List<String> selectedCols) {
        StringBuilder sb = new StringBuilder();
        for (String h : FIXED_HEADERS) sb.append(h).append(",");
        for (String c : selectedCols)  sb.append(c).append(",");
        if (sb.length() > 0) sb.setLength(sb.length() - 1);
        return sb.toString();
    }

    private String buildDataLine(LapTime lap, List<String> selectedCols, int lapNum) {
        StringBuilder sb = new StringBuilder();
        sb.append(lapNum).append(",");
        sb.append(lap.getFormatedTime()).append(",");
        sb.append(lap.getFormattedDelta()).append(",");
        sb.append(lap.getCarUsed().getMake()).append(" ").append(lap.getCarUsed().getModel()).append(",");

        List<String> headers = lap.getCarUsed().getColumnHeaders();
        List<String> values  = lap.getCarUsed().getColumnValues();
        for (String col : selectedCols) {
            int idx = headers.indexOf(col);
            sb.append(idx >= 0 && idx < values.size() ? values.get(idx) : "-").append(",");
        }
        if (sb.length() > 0) sb.setLength(sb.length() - 1);
        return sb.toString();
    }

    // -----------------------------------------------------------------------
    // PDF  (landscape A4, one row per lap)
    // -----------------------------------------------------------------------

    private static final float MARGIN      = 30f;
    private static final float ROW_HEIGHT  = 16f;
    private static final float HEADER_SIZE = 9f;
    private static final float CELL_SIZE   = 8f;

    private void exportPdf(List<LapTime> laps, List<String> selectedCols,
                           String path) throws IOException {

        List<String> allHeaders = buildAllHeaders(selectedCols);
        float colWidth = calculateColWidth(allHeaders.size());

        try (PDDocument doc = new PDDocument()) {
            PDPage page = newLandscapePage();
            doc.addPage(page);

            PDType1Font boldFont   = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDType1Font normalFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

            PDPageContentStream cs = new PDPageContentStream(doc, page);
            float pageWidth  = page.getMediaBox().getWidth();
            float pageHeight = page.getMediaBox().getHeight();
            float y = pageHeight - MARGIN;

            // Title
            cs.beginText();
            cs.setFont(boldFont, 12);
            cs.newLineAtOffset(MARGIN, y);
            cs.showText("Lap Data Export");
            cs.endText();
            y -= ROW_HEIGHT * 1.5f;

            // Header row
            y = drawRow(cs, allHeaders, MARGIN, y, colWidth, boldFont, HEADER_SIZE, true);

            // Data rows — start a new page when we run out of space
            int lapNum = 1;
            for (LapTime lap : laps) {
                if (y < MARGIN + ROW_HEIGHT) {
                    cs.close();
                    page = newLandscapePage();
                    doc.addPage(page);
                    cs = new PDPageContentStream(doc, page);
                    y = pageHeight - MARGIN;
                    y = drawRow(cs, allHeaders, MARGIN, y, colWidth, boldFont, HEADER_SIZE, true);
                }
                List<String> rowData = buildRowData(lap, selectedCols, lapNum++);
                y = drawRow(cs, rowData, MARGIN, y, colWidth, normalFont, CELL_SIZE, false);
            }

            cs.close();
            doc.save(path);
        }
    }

    private List<String> buildAllHeaders(List<String> selectedCols) {
        List<String> all = new ArrayList<>();
        for (String h : FIXED_HEADERS) all.add(h);
        all.addAll(selectedCols);
        return all;
    }

    private List<String> buildRowData(LapTime lap, List<String> selectedCols, int lapNum) {
        List<String> row = new ArrayList<>();
        row.add(String.valueOf(lapNum));
        row.add(lap.getFormatedTime());
        row.add(lap.getFormattedDelta());
        row.add(lap.getCarUsed().getMake() + " " + lap.getCarUsed().getModel());

        List<String> headers = lap.getCarUsed().getColumnHeaders();
        List<String> values  = lap.getCarUsed().getColumnValues();
        for (String col : selectedCols) {
            int idx = headers.indexOf(col);
            row.add(idx >= 0 && idx < values.size() ? values.get(idx) : "-");
        }
        return row;
    }

    private float drawRow(PDPageContentStream cs, List<String> cells,
                          float x, float y, float colWidth,
                          PDType1Font font, float fontSize, boolean header) throws IOException {
        float textY = y - ROW_HEIGHT + 4;

        // Background rectangle for header rows
        if (header) {
            cs.setNonStrokingColor(0.2f, 0.2f, 0.2f);
            cs.addRect(x, y - ROW_HEIGHT + 2, colWidth * cells.size(), ROW_HEIGHT);
            cs.fill();
        }

        // Draw each cell at its absolute x position using setTextMatrix
        for (int i = 0; i < cells.size(); i++) {
            float cellX = x + i * colWidth + 3;
            String text = truncate(cells.get(i), colWidth, font, fontSize);

            cs.beginText();
            cs.setFont(font, fontSize);
            if (header) {
                cs.setNonStrokingColor(1f, 1f, 1f);   // white text on dark background
            } else {
                cs.setNonStrokingColor(0f, 0f, 0f);   // black text
            }
            cs.setTextMatrix(Matrix.getTranslateInstance(cellX, textY));
            cs.showText(text);
            cs.endText();
        }

        cs.setNonStrokingColor(0f, 0f, 0f);
        return y - ROW_HEIGHT;
    }

    private PDPage newLandscapePage() {
        PDPage page = new PDPage(PDRectangle.A4);
        page.setMediaBox(new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth()));
        return page;
    }

    private float calculateColWidth(int colCount) {
        float usable = PDRectangle.A4.getHeight() - 2 * MARGIN; // landscape width
        return usable / colCount;
    }

    private String truncate(String text, float maxWidth,
                            PDType1Font font, float fontSize) {
        try {
            String t = text == null ? "" : text;
            while (t.length() > 1 &&
                   font.getStringWidth(t) / 1000f * fontSize > maxWidth - 4) {
                t = t.substring(0, t.length() - 1);
            }
            return t;
        } catch (IOException e) {
            return text;
        }
    }
}
