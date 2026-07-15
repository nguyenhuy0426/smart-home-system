package com.example.smart_home_mobile_app.data;

public final class BoundingBox {
    public final double left;
    public final double top;
    public final double right;
    public final double bottom;

    public BoundingBox(double left, double top, double right, double bottom) {
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
    }
}
