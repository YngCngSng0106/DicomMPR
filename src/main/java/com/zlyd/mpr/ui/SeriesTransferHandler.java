package com.zlyd.mpr.ui;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;

import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.TransferHandler;

import com.zlyd.mpr.dicom.SeriesInfo;

/**
 * 序列的拖拽支持：列表作为拖拽源，视图区作为放置目标。
 */
public final class SeriesTransferHandler {

    /** 同 JVM 内直接传递 SeriesInfo 对象的数据类型。 */
    public static final DataFlavor SERIES_FLAVOR;

    static {
        try {
            SERIES_FLAVOR = new DataFlavor(
                    DataFlavor.javaJVMLocalObjectMimeType + ";class=" + SeriesInfo.class.getName());
        } catch (ClassNotFoundException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private SeriesTransferHandler() {
    }

    /**
     * 列表作为拖拽源。
     */
    public static TransferHandler createSourceHandler(JList<SeriesEntry> list) {
        return new TransferHandler() {
            private static final long serialVersionUID = 1L;

            @Override
            public int getSourceActions(JComponent component) {
                return COPY;
            }

            @Override
            protected Transferable createTransferable(JComponent component) {
                SeriesEntry entry = list.getSelectedValue();
                return entry == null ? null : new SeriesTransferable(entry.getSeries());
            }
        };
    }

    /**
     * 视图区作为放置目标。
     */
    public static TransferHandler createTargetHandler(SeriesView view) {
        return new TransferHandler() {
            private static final long serialVersionUID = 1L;

            @Override
            public boolean canImport(TransferSupport support) {
                return support.isDataFlavorSupported(SERIES_FLAVOR);
            }

            @Override
            public boolean importData(TransferSupport support) {
                if (!canImport(support)) {
                    return false;
                }
                try {
                    SeriesInfo series = (SeriesInfo) support.getTransferable().getTransferData(SERIES_FLAVOR);
                    view.showSeries(series);
                    return true;
                } catch (UnsupportedFlavorException | IOException e) {
                    return false;
                }
            }
        };
    }

    /**
     * 承载单个 SeriesInfo 的 Transferable。
     */
    public static final class SeriesTransferable implements Transferable {

        private final SeriesInfo series;

        public SeriesTransferable(SeriesInfo series) {
            this.series = series;
        }

        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[]{SERIES_FLAVOR};
        }

        @Override
        public boolean isDataFlavorSupported(DataFlavor flavor) {
            return SERIES_FLAVOR.equals(flavor);
        }

        @Override
        public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
            if (!SERIES_FLAVOR.equals(flavor)) {
                throw new UnsupportedFlavorException(flavor);
            }
            return series;
        }
    }
}
