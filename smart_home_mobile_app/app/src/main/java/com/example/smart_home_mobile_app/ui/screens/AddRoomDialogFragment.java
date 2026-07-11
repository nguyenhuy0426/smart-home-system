package com.example.smart_home_mobile_app.ui.screens;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import com.example.smart_home_mobile_app.R;

/**
 * Bottom sheet thêm phòng mới.
 *
 * LƯU Ý: {@code SmartHomeAppController} hiện CHƯA có API tạo phòng trong Firebase
 * (chỉ có addHome() để thêm homeId vào danh sách nhà, khác với thêm 1 phòng bên
 * trong 1 nhà). Repository/CommandRepository hiện tại cũng chưa có hàm ghi
 * "homes/{homeId}/rooms/{roomId}". Vì vậy onSubmit() bên dưới chỉ gọi qua
 * {@link Listener}, còn phần ghi Firebase thật cần bổ sung sau (thêm 1 hàm
 * addRoom(homeId, name, callback) vào CommandRepository hoặc 1 RoomRepository mới,
 * theo đúng schema field mà HomeSnapshotParser đang đọc cho RoomSummary).
 */
public class AddRoomDialogFragment extends BottomSheetDialogFragment {

    /** Callback khi người dùng bấm "Thêm phòng" với tên hợp lệ. */
    public interface Listener {
        void onRoomAdded(String roomName);
    }

    @Nullable
    private Listener listener;

    public void setListener(@Nullable Listener listener) {
        this.listener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable android.view.ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_add_room, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        TextInputEditText etName = view.findViewById(R.id.et_room_name);
        View tvError = view.findViewById(R.id.tv_room_error);
        MaterialButton btnSubmit = view.findViewById(R.id.btn_submit_room);

        btnSubmit.setOnClickListener(v -> {
            String name = etName.getText() == null ? "" : etName.getText().toString().trim();
            if (name.isEmpty()) {
                tvError.setVisibility(View.VISIBLE);
                return;
            }
            tvError.setVisibility(View.GONE);
            if (listener != null) {
                listener.onRoomAdded(name);
            }
            dismiss();
        });
    }
}