package com.zlyd.mpr.geometry;

/**
 * 光标位置探测结果：所在视图、体素索引与体素值（CT 为 HU）。
 */
public final class VoxelProbe {

    private final int view;
    private final int i;
    private final int j;
    private final int k;
    private final double value;

    public VoxelProbe(int view, int i, int j, int k, double value) {
        this.view = view;
        this.i = i;
        this.j = j;
        this.k = k;
        this.value = value;
    }

    public int getView() {
        return view;
    }

    public int getI() {
        return i;
    }

    public int getJ() {
        return j;
    }

    public int getK() {
        return k;
    }

    public double getValue() {
        return value;
    }
}
