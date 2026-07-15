package com.example.smart_home_mobile_app.ui.screens;

import android.graphics.Typeface;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.smart_home_mobile_app.R;
import com.example.smart_home_mobile_app.data.HomeSnapshot;
import com.example.smart_home_mobile_app.data.MetricReading;
import com.example.smart_home_mobile_app.data.NodeSummary;
import com.example.smart_home_mobile_app.data.TelemetryReading;
import com.example.smart_home_mobile_app.ui.MobileDashboardActivity;
import com.example.smart_home_mobile_app.ui.SmartHomeAppController;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class NodeDetailsFragment extends Fragment implements SmartHomeAppController.Listener {
    private static final String ARG_NODE_ID = "nodeId";

    private SmartHomeAppController controller;
    private MaterialToolbar toolbar;
    private ViewGroup content;
    private String nodeId;

    public static NodeDetailsFragment newInstance(String nodeId) {
        NodeDetailsFragment fragment = new NodeDetailsFragment();
        Bundle args = new Bundle();
        args.putString(ARG_NODE_ID, nodeId);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_node_details, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        controller = ((MobileDashboardActivity) requireActivity()).getController();
        nodeId = getArguments() != null ? getArguments().getString(ARG_NODE_ID, "") : "";
        toolbar = view.findViewById(R.id.toolbar);
        content = view.findViewById(R.id.details_content);
        toolbar.setNavigationOnClickListener(v -> getParentFragmentManager().popBackStack());
    }

    @Override
    public void onStart() {
        super.onStart();
        controller.addListener(this);
        render();
    }

    @Override
    public void onStop() {
        super.onStop();
        controller.removeListener(this);
    }

    @Override
    public void onControllerStateChanged() {
        render();
    }

    private void render() {
        content.removeAllViews();
        HomeSnapshot snapshot = controller.homeState().snapshot;
        NodeSummary node = findNode(snapshot);
        toolbar.setTitle(node != null ? node.label : "Node");
        if (node == null) {
            TextView notFound = plainText("Node not found");
            notFound.setPadding(dp(8), dp(8), dp(8), dp(8));
            content.addView(notFound);
            return;
        }
        String role = snapshot != null ? snapshot.home.role : "";
        LayoutInflater inflater = LayoutInflater.from(requireContext());

        content.addView(buildSummaryCard(node));

        content.addView(sectionTitle("Latest telemetry"));
        TelemetryReading latest = node.latestReading();
        if (latest == null || latest.metrics.isEmpty()) {
            content.addView(plainText("No telemetry has been received"));
        } else {
            List<MetricReading> metrics = new ArrayList<>(latest.metrics.values());
            Collections.sort(metrics, (a, b) -> a.key.compareTo(b.key));
            for (MetricReading metric : metrics) {
                View card = inflater.inflate(R.layout.item_door_status_card, content, false);
                TextView nameView = card.findViewById(R.id.tv_door_name);
                if (nameView != null) nameView.setText(metric.key);
                TextView statusView = card.findViewById(R.id.tv_door_status);
                String value = metric.isValid()
                        ? metric.value + " " + metric.unit
                        : (metric.error != null ? metric.error : "Invalid");
                if (statusView != null) statusView.setText(metric.source + " · " + metric.validity + " | Value: " + value);
                
                TextView badge1 = card.findViewById(R.id.tv_fingerprint_badge);
                if (badge1 != null) badge1.setVisibility(View.GONE);
                TextView badge2 = card.findViewById(R.id.tv_card_badge);
                if (badge2 != null) badge2.setVisibility(View.GONE);
                TextView battery = card.findViewById(R.id.tv_door_battery);
                if (battery != null) battery.setVisibility(View.GONE);

                content.addView(card);
            }
        }

        content.addView(sectionTitle("Device actions"));
        if (node.actions.isEmpty()) {
            content.addView(plainText("No actions are declared by this node descriptor"));
        } else {
            for (final String action : node.actions) {
                boolean accessAction = action.equals("unlock") || action.equals("open_door");
                MaterialButton button = new MaterialButton(requireContext());
                button.setText("Request " + action.replace('_', ' '));
                button.setEnabled(!accessAction || "access_admin".equals(role));
                button.setLayoutParams(spacedParams());
                button.setOnClickListener(v -> controller.sendCommand(node.nodeId, action));
                content.addView(button);
                if (accessAction) {
                    TextView note = plainText("This creates an RTDB command request; it never unlocks a door "
                            + "directly. The gateway must authorize it.");
                    note.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                    content.addView(note);
                }
            }
        }
    }

    private View buildSummaryCard(NodeSummary node) {
        MaterialCardView card = new MaterialCardView(requireContext());
        LinearLayout column = new LinearLayout(requireContext());
        column.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        column.setPadding(pad, pad, pad, pad);
        column.addView(boldText(node.nodeId));
        column.addView(plainText("Type: " + node.nodeType));
        column.addView(plainText("Room: " + node.roomId));
        column.addView(plainText("Schema: " + node.schemaVersion));
        column.addView(plainText("Status: " + node.status));
        column.addView(plainText("Readings: " + node.readings.size()));
        card.addView(column);
        card.setLayoutParams(spacedParams());
        return card;
    }

    private NodeSummary findNode(HomeSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }
        for (NodeSummary node : snapshot.nodes) {
            if (node.nodeId.equals(nodeId)) {
                return node;
            }
        }
        return null;
    }

    private TextView sectionTitle(String text) {
        TextView title = new TextView(requireContext());
        title.setText(text);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setLayoutParams(spacedParams());
        return title;
    }

    private TextView boldText(String text) {
        TextView view = new TextView(requireContext());
        view.setText(text);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }

    private TextView plainText(String text) {
        TextView view = new TextView(requireContext());
        view.setText(text);
        return view;
    }

    private LinearLayout.LayoutParams spacedParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(14);
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
