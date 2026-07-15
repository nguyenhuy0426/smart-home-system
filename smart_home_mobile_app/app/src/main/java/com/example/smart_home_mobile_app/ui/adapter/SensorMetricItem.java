package com.example.smart_home_mobile_app.ui.adapter;

import com.example.smart_home_mobile_app.data.MetricReading;

/**
 * Wrapper nhỏ để {@link SensorAdapter} không cần biết {@code nodeType} thật của node.
 * MainFragment chịu trách nhiệm dò trong {@code NodeSummary.latestReading().metrics}
 * theo key metric mong muốn (xem hằng số METRIC_* trong MainFragment) và đóng gói
 * kết quả (có thể null nếu node không có reading hoặc không có key đó) vào đây.
 */
public final class SensorMetricItem {
    public final String label;
    /** Nullable: null khi node chưa có reading nào, hoặc reading không chứa metric này. */
    public final MetricReading reading;
    public final int iconRes;

    public SensorMetricItem(String label, MetricReading reading, int iconRes) {
        this.label = label;
        this.reading = reading;
        this.iconRes = iconRes;
    }

    /** true nếu có dữ liệu và dữ liệu hợp lệ (MetricReading.isValid()). */
    public boolean hasValidReading() {
        return reading != null && reading.isValid();
    }

    /** Chuỗi hiển thị giá trị, ví dụ "22.5°C". Trả về "—" nếu không có dữ liệu hợp lệ. */
    public String displayValue() {
        if (!hasValidReading()) {
            return "—";
        }
        String unit = reading.unit == null ? "" : reading.unit;
        return formatNumber(reading.value) + unit;
    }

    private static String formatNumber(double value) {
        if (value == Math.rint(value)) {
            return String.valueOf((long) value);
        }
        return String.valueOf(value);
    }
}