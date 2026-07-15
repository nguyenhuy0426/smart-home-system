package com.example.smart_home_mobile_app.ui.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.smart_home_mobile_app.R;
import com.example.smart_home_mobile_app.data.RoomSummary;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RoomAdapter extends RecyclerView.Adapter<RoomAdapter.RoomViewHolder> {

    /** Callback khi người dùng chọn một phòng. */
    public interface OnRoomClickListener {
        void onRoomClick(RoomSummary room);
    }

    private final List<RoomSummary> items = new ArrayList<>();
    private final Set<String> selectedRoomIds = new HashSet<>();
    private final OnRoomClickListener listener;

    public RoomAdapter(OnRoomClickListener listener) {
        this.listener = listener;
    }

    public void submitList(List<RoomSummary> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    /** Chọn phòng theo id, bỏ chọn các phòng còn lại. */
    public void selectRoom(String roomId) {
        selectedRoomIds.clear();
        selectedRoomIds.add(roomId);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public RoomViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_room_chip, parent, false);
        return new RoomViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RoomViewHolder holder, int position) {
        RoomSummary item = items.get(position);
        boolean selected = selectedRoomIds.contains(item.roomId);
        holder.bind(item, selected);
        holder.itemView.setOnClickListener(v -> {
            selectRoom(item.roomId);
            if (listener != null) listener.onRoomClick(item);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class RoomViewHolder extends RecyclerView.ViewHolder {
        private final View root;
        private final TextView tvName;
        private final TextView tvCount;

        RoomViewHolder(@NonNull View itemView) {
            super(itemView);
            root = itemView.findViewById(R.id.room_chip_root);
            tvName = itemView.findViewById(R.id.tv_room_name);
            tvCount = itemView.findViewById(R.id.tv_room_count);
        }

        void bind(RoomSummary item, boolean selected) {
            tvName.setText(item.label);
            // RoomSummary không có field "deviceCount" riêng, dùng kích thước
            // danh sách nodeIds của phòng làm số lượng thiết bị/cảm biến.
            tvCount.setText("(" + item.nodeIds.size() + ")");

            Context context = itemView.getContext();
            if (selected) {
                root.setBackgroundResource(R.drawable.bg_pill_selected);
                tvName.setTextColor(context.getColor(R.color.sh_bg));
                tvCount.setTextColor(context.getColor(R.color.sh_bg));
            } else {
                root.setBackgroundResource(R.drawable.bg_pill);
                tvName.setTextColor(context.getColor(R.color.sh_text_primary));
                tvCount.setTextColor(context.getColor(R.color.sh_text_secondary));
            }
        }
    }
}