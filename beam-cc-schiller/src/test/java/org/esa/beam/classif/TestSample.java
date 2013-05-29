package org.esa.beam.classif;


import org.esa.beam.framework.datamodel.RasterDataNode;
import org.esa.beam.framework.gpf.pointop.WritableSample;

public class TestSample implements WritableSample{

    private double dVal;
    private int iVal;
    private float fVal;

    @Override
    public void set(int bitIndex, boolean v) {
    }

    @Override
    public void set(boolean v) {
    }

    @Override
    public void set(int v) {
        iVal = v;
    }

    @Override
    public void set(float v) {
        this.fVal = v;
    }

    @Override
    public void set(double v) {
        dVal = v;
    }

    @Override
    public RasterDataNode getNode() {
        return null;
    }

    @Override
    public int getIndex() {
        return 0;
    }

    @Override
    public int getDataType() {
        return 0;
    }

    @Override
    public boolean getBit(int bitIndex) {
        return false;
    }

    @Override
    public boolean getBoolean() {
        return false;
    }

    @Override
    public int getInt() {
        return iVal;
    }

    @Override
    public float getFloat() {
        return fVal;
    }

    @Override
    public double getDouble() {
        return dVal;
    }
}
