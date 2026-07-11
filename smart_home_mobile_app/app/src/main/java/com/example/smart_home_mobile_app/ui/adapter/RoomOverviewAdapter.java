package com.example.smart_home_mobile_app.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.smart_home_mobile_app.R;
import com.example.smart_home_mobile_app.data.NodeSummary;
import com.example.smart_home_mobile_app.data.RoomSummary;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class RoomOverviewAdapter extends RecyclerView.Adapter<RoomOverviewAdapter.RoomOverviewViewHolder> {
    public interface OnRoomClickListener {
        void onRoomClick(RoomSummary room);
    }

    private final List<RoomSummary> rooms = new ArrayList<>();
    private final List<NodeSummary> nodes = new ArrayList<>();
    private final OnRoomClickListener listener;

    public RoomOverviewAdapter(OnRoomClickListener listener) {
        this.listener = listener;
    }

    public void submitList(List<RoomSummary> newRooms, List<NodeSummary> newNodes) {
        rooms.clear();
        nodes.clear();
        if (newRooms != null) rooms.addAll(newRooms);
        if (newNodes != null) nodes.addAll(newNodes);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public RoomOverviewViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_room_overview_card, parent, false);
        return new RoomOverviewViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RoomOverviewViewHolder holder, int position) {
        RoomSummary room = rooms.get(position);
        holder.bind(room, countNodes(room), roomImage(room.label));
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onRoomClick(room);
        });
    }

    @Override
    public int getItemCount() {
        return rooms.size();
    }

    private int countNodes(RoomSummary room) {
        int count = 0;
        for (NodeSummary node : nodes) {
            if (room.roomId.equals(node.roomId)) count++;
        }
        return count > 0 ? count : room.nodeIds.size();
    }

    private static int roomImage(String label) {
        String key = stripAccent(label == null ? "" : label).toLowerCase(Locale.US);
        if (key.contains("ngu") || key.contains("bed")) return R.drawable.img_room_bedroom;
        if (key.contains("bep") || key.contains("kitchen")) return R.drawable.img_room_kitchen;
        if (key.contains("tam") || key.contains("bath") || key.contains("wc")) return R.drawable.img_room_bathroom;
        if (key.contains("khach") || key.contains("living")) return R.drawable.img_room_living;
        return R.drawable.img_room_living;
    }

    private static String stripAccent(String input) {
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{InCombiningDiacriticalMarks}+", "").replace('đ', 'd').replace('Đ', 'D');
    }

    static class RoomOverviewViewHolder extends RecyclerView.ViewHolder {
        private final ImageView imgPhoto;
        private final TextView tvName;
        private final TextView tvCount;
        private final ImageView imgIcon;

        RoomOverviewViewHolder(@NonNull View itemView) {
            super(itemView);
            imgPhoto = itemView.findViewById(R.id.img_room_card_photo);
            tvName = itemView.findViewById(R.id.tv_room_card_name);
            tvCount = itemView.findViewById(R.id.tv_room_card_count);
            imgIcon = itemView.findViewById(R.id.img_room_card_icon);
        }

        void bind(RoomSummary room, int nodeCount, int imageRes) {
            imgPhoto.setImageResource(imageRes);
            tvName.setText(room.label);
            tvCount.setText(nodeCount + " thiết bị");
            imgIcon.setImageResource(R.drawable.ic_tab_home);
        }
    }
}
