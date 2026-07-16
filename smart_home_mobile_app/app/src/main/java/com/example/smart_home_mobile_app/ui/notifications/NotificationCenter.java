package com.example.smart_home_mobile_app.ui.notifications;

import com.example.smart_home_mobile_app.data.CommandRequest;
import com.example.smart_home_mobile_app.data.HomeSnapshot;
import com.example.smart_home_mobile_app.data.MetricReading;
import com.example.smart_home_mobile_app.data.NodeSummary;
import com.example.smart_home_mobile_app.data.RoomSummary;
import com.example.smart_home_mobile_app.data.TelemetryReading;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class NotificationCenter {
    /*
     * Ngưỡng mặc định phía ứng dụng. Khi backend cung cấp cấu hình ngưỡng theo
     * từng nhà/thiết bị, thay các giá trị này bằng cấu hình trong HomeSnapshot.
     */
    private static final List<MetricRule> SENSOR_RULES = Arrays.asList(
            new MetricRule("temperature", "nhiệt độ", 10.0, 35.0, "°C"),
            new MetricRule("humidity", "độ ẩm", 30.0, 80.0, "%"),
            new MetricRule("smoke", "khói", null, 50.0, "ppm"),
            new MetricRule("pm25", "bụi mịn PM2.5", null, 35.0, "µg/m³")
    );

    private NotificationCenter() {
    }

    public static List<NotificationItem> build(HomeSnapshot snapshot, String currentUid,
                                               String currentEmail) {
        List<NotificationItem> result = new ArrayList<>();
        if (snapshot == null) return result;

        Map<String, NodeSummary> nodesById = new HashMap<>();
        for (NodeSummary node : snapshot.nodes) {
            nodesById.put(node.nodeId, node);
        }

        Map<String, String> roomLabels = new HashMap<>();
        for (RoomSummary room : snapshot.rooms) {
            roomLabels.put(room.roomId, room.label);
        }

        for (CommandRequest command : snapshot.commandRequests) {
            NodeSummary node = nodesById.get(command.targetNodeId);
            String nodeLabel = node == null || node.label.trim().isEmpty()
                    ? command.targetNodeId
                    : node.label;
            if (nodeLabel == null || nodeLabel.trim().isEmpty()) nodeLabel = "thiết bị";

            String actor = actorLabel(command.requestedBy, currentUid, currentEmail);
            String description = actor + " đã gửi lệnh " + actionLabel(command.commandType)
                    + " • " + statusLabel(command.status);
            result.add(new NotificationItem(
                    "command:" + command.requestId,
                    NotificationItem.Kind.COMMAND,
                    "Điều khiển " + nodeLabel,
                    description,
                    command.createdAtEpochMs));
        }

        for (NodeSummary node : snapshot.nodes) {
            addSensorAlerts(result, node, roomLabels.get(node.roomId));
        }

        Collections.sort(result, (left, right) -> {
            int byTime = Long.compare(right.timestampEpochMs, left.timestampEpochMs);
            return byTime != 0 ? byTime : left.id.compareTo(right.id);
        });
        return result;
    }

    private static void addSensorAlerts(List<NotificationItem> result, NodeSummary node,
                                        String roomLabel) {
        List<TelemetryReading> readings = new ArrayList<>(node.readings);
        Collections.sort(readings,
                (left, right) -> Long.compare(left.timestampEpochMs(), right.timestampEpochMs()));

        Map<String, String> previousBreachByMetric = new HashMap<>();
        for (TelemetryReading telemetry : readings) {
            for (MetricRule rule : SENSOR_RULES) {
                MetricReading metric = telemetry.metrics.get(rule.key);
                if (metric == null || !metric.isValid()) continue;

                String breach = rule.breach(metric.value);
                String previousBreach = previousBreachByMetric.get(rule.key);
                if (breach != null && !breach.equals(previousBreach)) {
                    String location = roomLabel == null || roomLabel.trim().isEmpty()
                            ? node.label
                            : node.label + " • " + roomLabel;
                    result.add(new NotificationItem(
                            "sensor:" + node.nodeId + ":" + rule.key + ":" + telemetry.readingId,
                            NotificationItem.Kind.SENSOR_ALERT,
                            "Cảnh báo " + rule.label,
                            location + " ghi nhận " + formatNumber(metric.value) + " " + rule.unit
                                    + ", " + rule.thresholdMessage(breach),
                            telemetry.timestampEpochMs()));
                }

                if (breach == null) {
                    previousBreachByMetric.remove(rule.key);
                } else {
                    previousBreachByMetric.put(rule.key, breach);
                }
            }
        }
    }

    private static String actorLabel(String requestedBy, String currentUid, String currentEmail) {
        if (requestedBy == null || requestedBy.trim().isEmpty()) {
            return "Không rõ người điều khiển";
        }
        if (requestedBy.equals(currentUid)) {
            return currentEmail == null || currentEmail.trim().isEmpty()
                    ? "Bạn"
                    : currentEmail.trim();
        }
        return "Thành viên " + shortenIdentifier(requestedBy);
    }

    private static String shortenIdentifier(String value) {
        if (value.length() <= 16) return value;
        return value.substring(0, 8) + "…" + value.substring(value.length() - 4);
    }

    private static String actionLabel(String action) {
        if (action == null) return "không xác định";
        switch (action) {
            case "toggle":
                return "bật/tắt";
            case "set_mode":
                return "đổi chế độ";
            case "set_intensity":
                return "điều chỉnh mức";
            case "unlock":
                return "mở khóa";
            case "open_door":
                return "mở cửa";
            case "lock":
                return "khóa cửa";
            default:
                return action.replace('_', ' ');
        }
    }

    private static String statusLabel(String status) {
        if (status == null) return "Không rõ trạng thái";
        switch (status.toLowerCase(Locale.ROOT)) {
            case "pending":
                return "Đang chờ xử lý";
            case "accepted":
            case "approved":
                return "Đã chấp nhận";
            case "completed":
            case "success":
                return "Đã thực hiện";
            case "rejected":
            case "denied":
                return "Đã từ chối";
            case "failed":
            case "error":
                return "Thực hiện thất bại";
            default:
                return status;
        }
    }

    private static String formatNumber(double value) {
        NumberFormat formatter = NumberFormat.getNumberInstance(new Locale("vi", "VN"));
        formatter.setMaximumFractionDigits(1);
        formatter.setMinimumFractionDigits(0);
        return formatter.format(value);
    }

    private static final class MetricRule {
        final String key;
        final String label;
        final Double min;
        final Double max;
        final String unit;

        MetricRule(String key, String label, Double min, Double max, String unit) {
            this.key = key;
            this.label = label;
            this.min = min;
            this.max = max;
            this.unit = unit;
        }

        String breach(double value) {
            if (min != null && value < min) return "low";
            if (max != null && value > max) return "high";
            return null;
        }

        String thresholdMessage(String breach) {
            if ("low".equals(breach)) {
                return "thấp hơn ngưỡng " + formatNumber(min) + " " + unit;
            }
            return "vượt ngưỡng " + formatNumber(max) + " " + unit;
        }
    }
}
