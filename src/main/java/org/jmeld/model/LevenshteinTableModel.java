package org.jmeld.model;

import org.jmeld.diff.JMChunk;
import org.jmeld.diff.JMDelta;
import org.jmeld.diff.JMRevision;
import org.jmeld.ui.BufferDiffPanel;
import org.jmeld.ui.FilePanel;
import org.jmeld.ui.text.BufferDocumentIF;
import org.jmeld.ui.util.Colors;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * Modelo de levenstein
 * User: alberto
 * Date: 7/12/12
 * Time: 16:40
 */
public class LevenshteinTableModel extends DefaultTableModel {
    String origin;
    String destiny;
    private JMRevision currentRevision;
    private FilePanel[] filePanels;
    private HashMap<Point,Color> routeDiff;
    private HashMap<Point, Border> borderChunks;
    private boolean showSelectionPath;

    public void buildModel() {
        if (!isAllDataAvaliable()) {
            return;
        }
        setDataVector(newVector(origin.length() + 2), newVector(destiny.length() + 2));
        buildSolutionPath();
        int rowCount = getRowCount();
        int columnCount = getColumnCount();

        for (int columna = 0; columna < columnCount; columna++) {
            if (columna < 2) {
                setValueAt("", 0, columna);
            } else {
                setValueAt(destiny.charAt(columna - 2), 0, columna);
            }
        }

        for (int fila = 0; fila < rowCount; fila++) {
            if (fila < 2) {
                setValueAt("", fila, 0);
            } else {
                setValueAt(origin.charAt(fila - 2), fila, 0);
            }
        }

        int minLength = rowCount > columnCount ? columnCount : rowCount;

        for (int diagonal = 1; diagonal < minLength; diagonal++) {

            if (diagonal == 1) {
                setValueAt(0, diagonal, diagonal);
            } else {
                Object o = getValueAt(diagonal - 1, diagonal - 1);
                int incremento;

                if (origin.charAt(diagonal - 2) == destiny.charAt(diagonal - 2)) {
                    incremento = 0;
                } else {
                    incremento = 1;
                }

                setValueAt((Integer) o + incremento, diagonal, diagonal);
            }

            for (int columna = diagonal + 1; columna < columnCount; columna++) {
                setValueAt((Integer) getValueAt(diagonal, columna - 1) + 1, diagonal, columna);
            }

            for (int fila = diagonal + 1; fila < rowCount; fila++) {
                setValueAt((Integer) getValueAt(fila - 1, diagonal) + 1, fila, diagonal);
            }
        }
    }

    private boolean isAllDataAvaliable() {
        return getFilePanels() != null && getCurrentRevision() != null && origin != null && destiny != null;
    }

    class ColorPoint extends Point {
        Color color;
        public ColorPoint(int x, int y, Color color) {
            super(x, y);
            this.color = color;
        }
    }

    private void buildSolutionPath() {
        BufferDocumentIF originalBufferDocument = filePanels[BufferDiffPanel.LEFT].getBufferDocument();
        BufferDocumentIF revisedBufferDocument = filePanels[BufferDiffPanel.RIGHT].getBufferDocument();
        java.util.List<JMDelta> deltas = currentRevision.getDeltas();
        routeDiff = new HashMap<>();
        borderChunks = new HashMap<>();
        int xOffset = 0;
        int yOffset = 0;
        for (Iterator<JMDelta> iterator = deltas.iterator(); iterator.hasNext(); ) {
            JMDelta next = iterator.next();
            JMChunk original = next.getOriginal();
            JMChunk revised = next.getRevised();
            int offsetForLineOriginal = originalBufferDocument.getOffsetForLine(original.getAnchor());
            int offsetForLineRevised = revisedBufferDocument.getOffsetForLine(revised.getAnchor());
            for (int i = 0; i < offsetForLineOriginal - xOffset; i++) {
                routeDiff.put(new Point(i + xOffset, i + yOffset), Color.GRAY);
            }
            if (next.isAdd()) {
                int yOffsetForLine = revisedBufferDocument.getOffsetForLine(revised.getAnchor());
                int lineNumber = revised.getAnchor() + revised.getSize() - 1;
                int yOffsetForLineEnd = revisedBufferDocument.getOffsetForLine(lineNumber) +
                        + revisedBufferDocument.getLineText(lineNumber).length();
                for (int i = 0; i < yOffsetForLineEnd - yOffsetForLine; i++) {
                    Border border;
                    if (i == 0) {
                        border = BorderFactory.createMatteBorder(2, 2, 2, 0, Color.BLACK);
                    } else if (i == yOffsetForLineEnd - yOffsetForLine - 1) {
                        border = BorderFactory.createMatteBorder(2, 0, 2, 2, Color.BLACK);
                    } else {
                        border = BorderFactory.createMatteBorder(2, 0, 2, 0, Color.BLACK);
                    }
                    Point point = new Point(offsetForLineOriginal - 1, i + yOffsetForLine);
                    borderChunks.put(point, border);
                    routeDiff.put(point, Colors.ADDED);
                }
                xOffset = offsetForLineOriginal;
                yOffset = yOffsetForLineEnd;
            } else if (next.isDelete()) {
                int lineNumber = original.getAnchor() + original.getSize() - 1;
                int xOffsetForLine = originalBufferDocument.getOffsetForLine(lineNumber)
                        + originalBufferDocument.getLineText(lineNumber).length();
                for (int i = 0; i < xOffsetForLine - offsetForLineOriginal; i++) {
                    Point point = new Point(i + offsetForLineOriginal, offsetForLineRevised - 1);
                    routeDiff.put(point, Colors.DELETED);
                    Border border;
                    if (i == 0) {
                        border = BorderFactory.createMatteBorder(2, 2, 0, 2, Color.BLACK);
                    } else if (i == xOffsetForLine - offsetForLineOriginal - 1) {
                        border = BorderFactory.createMatteBorder(0, 2, 2, 2, Color.BLACK);
                    } else {
                        border = BorderFactory.createMatteBorder(0, 2, 0, 2, Color.BLACK);
                    }
                    borderChunks.put(point, border);
                }
                xOffset = xOffsetForLine;
                yOffset = offsetForLineRevised;
            } else if (next.isChange()) {
                List<JMDelta> changedDeltas = next.getChangeRevision().getDeltas();
                int startOffset = 0;
                for (Iterator<JMDelta> jmDeltaIterator = changedDeltas.iterator(); jmDeltaIterator.hasNext(); ) {
                    JMDelta changeDelta = jmDeltaIterator.next();

                    JMChunk originalChange = changeDelta.getOriginal();
                    JMChunk revisedChange = changeDelta.getRevised();
                    int sameChars = originalChange.getAnchor();
                    xOffset = offsetForLineOriginal;
                    yOffset = offsetForLineRevised;
                    for (int i = 0; i < sameChars - startOffset; i++) {
                        routeDiff.put(new Point(i + xOffset + startOffset, i + yOffset + startOffset), Color.GRAY);
                    }
                    startOffset = sameChars + originalChange.getSize();
                    xOffset += sameChars;
                    yOffset += sameChars;
                    int removeFromOrigin = originalChange.getSize();
                    int addFromRevised = revisedChange.getSize();
                    for (int i = 0; i < removeFromOrigin; i++) {
                        Point point = new Point(i + xOffset, yOffset - 1);
                        routeDiff.put(point, Colors.DELETED);
                        Border border;
                        if (i == 0) {
                            for (int j = 0; j < addFromRevised; j++) {
                                if (j == addFromRevised - 1) {
                                    border = BorderFactory.createMatteBorder(2, 0, 0, 2, Color.BLACK);
                                } else {
                                    border = BorderFactory.createMatteBorder(2, 0, 0, 0, Color.BLACK);
                                }
                                borderChunks.put(new Point(i + xOffset, yOffset + j), border);
                            }
                            border = BorderFactory.createMatteBorder(2, 2, 0, 0, Color.BLACK);
                        } else if (i == removeFromOrigin - 1) {
                            border = BorderFactory.createMatteBorder(0, 2, 2, 0, Color.BLACK);
                        } else {
                            border = BorderFactory.createMatteBorder(0, 0, 0, 2, Color.BLACK);
                            borderChunks.put(new Point(i + xOffset, yOffset - 1 + addFromRevised), border);
                            border = BorderFactory.createMatteBorder(0, 2, 0, 0, Color.BLACK);
                        }
                        borderChunks.put(point, border);
                    }
                    xOffset += removeFromOrigin;
                    for (int i = 0; i < addFromRevised; i++) {
                        Point point = new Point(xOffset - 1, i + yOffset);
                        routeDiff.put(point, Colors.ADDED);
                        Border border;
                        /*if (i == 0) {
                            border = BorderFactory.createMatteBorder(2, 2, 0, 0, Color.BLACK);
                        } else */
                        if (i == addFromRevised - 1) {
                            border = BorderFactory.createMatteBorder(0, 0, 2, 2, Color.BLACK);
                        } else {
                            border = BorderFactory.createMatteBorder(0, 0, 2, 0, Color.BLACK);
                        }
                        borderChunks.put(point, border);
                    }
                    yOffset+=addFromRevised;
                    //offsetForLineRevised++;
                }
            }
        }
        int xSize = originalBufferDocument.getDocument().getLength();
        for (int i = 0; i < xSize - xOffset; i++) {
            routeDiff.put(new Point(i + xOffset, i + yOffset), Color.GRAY);
        }
    }

    private Vector newVector(int size) {
        Vector vector = new Vector(size);
        vector.setSize(size);
        return vector;
    }

    public String getOrigin() {
        return origin;
    }

    public void setOrigin(String origin) {
        this.origin = origin;
        buildModel();
    }

    public String getDestiny() {
        return destiny;
    }

    public void setDestiny(String destiny) {
        this.destiny = destiny;
        buildModel();
    }

    public DefaultTableCellRenderer getCellRenderer() {
        return new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                if ("\n".equals(value.toString())) {
                    value = "\\n";
                }
                Component tableCellRendererComponent = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                Color background = Color.WHITE;
                if (row < 2 && column < 2) {
                    if (row == column) {
                        background = Color.GREEN;
                    } else {
                        background = Color.BLUE;
                    }
                } else {
                    if (row > 1) {
                        if (column > 1) {
                            if (origin.charAt(row - 2) == destiny.charAt(column - 2)) {
                                background = Color.ORANGE;
                            }
                        }
                    }

                    if (row == 0 || column == 0) {
                        background = new Color(0x550099FF, true);
                    }

                }
                if (isSelected || table.isColumnSelected(column)) {
                    Color newColor = Color.CYAN;
                    Color mixColor = Color.WHITE;
                    background = new Color(background.getRed() * newColor.getRed()/mixColor.getRed()
                    ,background.getGreen() * newColor.getGreen()/mixColor.getGreen()
                            ,background.getBlue() * newColor.getBlue()/mixColor.getBlue());
                }

                Point point = new Point(row - 2, column - 2);
                if (showSelectionPath) {
                    Color color = routeDiff.get(point);
                    if (color != null) {
                        background = color;
                    }
                }

                Border border = borderChunks.get(point);
                if (border != null) {
                    ((JLabel)tableCellRendererComponent).setBorder(border);
                }
                tableCellRendererComponent.setBackground(background);
                ((JLabel) tableCellRendererComponent).setHorizontalAlignment(SwingConstants.CENTER);
                return tableCellRendererComponent;
            }
        };
    }

    public void setCurrentRevision(JMRevision currentRevision) {
        this.currentRevision = currentRevision;
        buildModel();
    }

    public JMRevision getCurrentRevision() {
        return currentRevision;
    }

    public void setFilePanels(FilePanel[] filePanels) {
        this.filePanels = filePanels;
        buildModel();
    }

    public FilePanel[] getFilePanels() {
        return filePanels;
    }

    public boolean isShowSelectionPath() {
        return showSelectionPath;
    }

    public void setShowSelectionPath(boolean showSelectionPath) {
        this.showSelectionPath = showSelectionPath;
    }
}
