package com.example.smart_home_mobile_app.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.smart_home_mobile_app.R;
import com.example.smart_home_mobile_app.repository.UserHomesRepository.HomeListItem;

import java.util.ArrayList;
import java.util.List;

public class HomeListAdapter extends RecyclerView.Adapter<HomeListAdapter.HomeViewHolder> {
    public interface Listener {
        void onHomeSelected(String homeId);
    }

    private final List<HomeListItem> items = new ArrayList<>();
    private final Listener listener;
    private String selectedHomeId;

    public HomeListAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submitList(List<HomeListItem> homes, String selectedHomeId) {
        items.clear();
        items.addAll(homes);
        this.selectedHomeId = selectedHomeId;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public HomeViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_home_card, parent, false);
        return new HomeViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull HomeViewHolder holder, int position) {
        HomeListItem item = items.get(position);
        holder.bind(item, item.homeId.equals(selectedHomeId));
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onHomeSelected(item.homeId);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class HomeViewHolder extends RecyclerView.ViewHolder {
        private final TextView title;
        private final TextView subtitle;
        private final TextView status;

        HomeViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.tv_home_name);
            subtitle = itemView.findViewById(R.id.tv_home_hint);
            status = itemView.findViewById(R.id.tv_home_status);
        }

        void bind(HomeListItem item, boolean selected) {
            title.setText(item.name == null || item.name.trim().isEmpty() ? item.homeId : item.name);
            subtitle.setText(selected ? "Nhà đang mở" : "Chạm để mở nhà này");
            status.setText(selected ? "Đang chọn" : "Mở");
            status.setBackgroundResource(selected ? R.drawable.bg_pill_selected : R.drawable.bg_pill);
            int statusColor = itemView.getContext().getColor(selected ? R.color.sh_bg : R.color.sh_text_primary);
            status.setTextColor(statusColor);
        }
    }
}
