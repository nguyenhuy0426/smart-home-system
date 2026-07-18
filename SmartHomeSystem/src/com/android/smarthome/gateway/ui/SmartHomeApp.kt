package com.android.smarthome.gateway.ui

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.smarthome.data.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class TopTab(val label: String, val symbol: String) {
    HOME("Trang chủ", "⌂"), ROOMS("Phòng", "▦"), NOTIFICATIONS("Thông báo", "●"), ACCOUNT("Tôi", "☺")
}

private sealed interface Route {
    data object Root : Route
    data object ManageHomes : Route
    data object CreateHome : Route
    data object JoinHome : Route
    data class RoomDetail(val id: String) : Route
    data class NodeDetail(val id: String) : Route
    data object Camera : Route
}

@Composable
fun SmartHomeApp(
    controller: SmartHomeController,
    cameraBitmap: Bitmap?,
    gatewayStatus: String,
    firebaseStatus: String,
    onLogout: () -> Unit
) {
    var state by remember { mutableStateOf(controller.state) }
    var tab by remember { mutableStateOf(TopTab.HOME) }
    val backStack = remember { mutableStateListOf<Route>(Route.Root) }
    val route = backStack.last()
    val listener = remember { SmartHomeController.Listener { state = it } }
    DisposableEffect(controller) {
        controller.addListener(listener)
        controller.start()
        onDispose { controller.removeListener(listener) }
    }
    BackHandler(backStack.size > 1) { backStack.removeAt(backStack.lastIndex) }
    fun open(value: Route) { backStack += value }

    SmartHomeTheme {
        Scaffold(
            containerColor = SmartHomeColors.Background,
            bottomBar = {
                if (route == Route.Root) {
                    NavigationBar(containerColor = SmartHomeColors.Card) {
                        TopTab.entries.forEach { item ->
                            NavigationBarItem(
                                selected = tab == item,
                                onClick = { tab = item },
                                icon = { Text(item.symbol, fontSize = 22.sp) },
                                label = { Text(item.label, maxLines = 1) }
                            )
                        }
                    }
                }
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (val current = route) {
                    Route.Root -> when (tab) {
                        TopTab.HOME -> HomeScreen(state, controller, { open(Route.NodeDetail(it)) }, { open(Route.Camera) })
                        TopTab.ROOMS -> RoomsScreen(state) { open(Route.RoomDetail(it)) }
                        TopTab.NOTIFICATIONS -> NotificationsScreen(state)
                        TopTab.ACCOUNT -> AccountScreen(state, { open(Route.ManageHomes) }, onLogout)
                    }
                    Route.ManageHomes -> ManageHomesScreen(state, controller, { backStack.removeAt(backStack.lastIndex) },
                        { open(Route.CreateHome) }, { open(Route.JoinHome) })
                    Route.CreateHome -> CreateHomeScreen(controller) { backStack.removeAt(backStack.lastIndex) }
                    Route.JoinHome -> JoinHomeScreen(controller) { backStack.removeAt(backStack.lastIndex) }
                    is Route.RoomDetail -> RoomDetailScreen(current.id, state, controller) { backStack.removeAt(backStack.lastIndex) }
                    is Route.NodeDetail -> NodeDetailsScreen(current.id, state, controller) { backStack.removeAt(backStack.lastIndex) }
                    Route.Camera -> CameraScreen(state, controller, cameraBitmap, gatewayStatus, firebaseStatus) {
                        backStack.removeAt(backStack.lastIndex)
                    }
                }
            }
        }
    }
}

@Composable
fun OnboardingScreen(onContinue: () -> Unit) {
    SmartHomeTheme {
        Box(Modifier.fillMaxSize().background(SmartHomeColors.Background)) {
            Column(
                Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 44.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text("VIBON / SMART HOME", color = SmartHomeColors.Accent, fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp)
                Column {
                    Box(Modifier.size(112.dp).clip(CircleShape).background(SmartHomeColors.AccentSoft),
                        contentAlignment = Alignment.Center) { Text("⌂", fontSize = 58.sp, color = SmartHomeColors.Accent) }
                    Spacer(Modifier.height(28.dp))
                    Text("Your Smart Home\nComfort Starts Here", fontSize = 42.sp, lineHeight = 46.sp,
                        fontWeight = FontWeight.Bold)
                    Text("Theo dõi, bảo vệ và điều khiển ngôi nhà từ một nơi duy nhất.",
                        color = SmartHomeColors.TextSecondary, modifier = Modifier.padding(top = 16.dp))
                }
                Button(onClick = onContinue, modifier = Modifier.fillMaxWidth().height(58.dp)) {
                    Text("Get Started  →", fontSize = 17.sp)
                }
            }
        }
    }
}

@Composable
fun AuthContent(
    onPasswordSubmit: (Boolean, String, String) -> Unit,
    onProvider: (String) -> Unit,
    onPreview: (() -> Unit)? = null,
    loading: Boolean
) {
    var login by remember { mutableStateOf(true) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    Box(Modifier.fillMaxSize().background(SmartHomeColors.Background), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 440.dp).fillMaxWidth().padding(24.dp)
                .clip(RoundedCornerShape(28.dp)).background(SmartHomeColors.Card).padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("VIBON", color = SmartHomeColors.Accent, fontSize = 30.sp, fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp)
            Text(if (login) "Chào mừng trở lại" else "Tạo tài khoản mới",
                color = SmartHomeColors.TextSecondary, modifier = Modifier.padding(bottom = 24.dp))
            OutlinedTextField(email, { email = it }, label = { Text("Email") }, singleLine = true,
                modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(password, { password = it }, label = { Text("Mật khẩu") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            if (!login) {
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(confirmation, { confirmation = it }, label = { Text("Nhập lại mật khẩu") },
                    singleLine = true, visualTransformation = PasswordVisualTransformation(),
                    isError = confirmation.isNotEmpty() && confirmation != password, modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { if (login || confirmation == password) onPasswordSubmit(login, email, password) },
                enabled = !loading && (login || confirmation == password), modifier = Modifier.fillMaxWidth().height(52.dp)
            ) { if (loading) CircularProgressIndicator(Modifier.size(22.dp)) else Text(if (login) "Đăng nhập" else "Đăng ký") }
            Text("Hoặc tiếp tục với", color = SmartHomeColors.TextSecondary, fontSize = 12.sp,
                modifier = Modifier.padding(vertical = 14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("google" to "Google", "facebook" to "Facebook", "apple" to "Apple").forEach { (id, label) ->
                    OutlinedButton(onClick = { onProvider(id) }, modifier = Modifier.weight(1f)) { Text(label, fontSize = 12.sp) }
                }
            }
            TextButton(onClick = { login = !login }, enabled = !loading) {
                Text(if (login) "Chưa có tài khoản? Đăng ký" else "Đã có tài khoản? Đăng nhập")
            }
            onPreview?.let { preview ->
                TextButton(onClick = preview, enabled = !loading) { Text("Xem trước giao diện (Debug)") }
            }
        }
    }
}

@Composable
private fun HomeScreen(
    state: SmartHomeController.State,
    controller: SmartHomeController,
    onNode: (String) -> Unit,
    onCamera: () -> Unit
) {
    val snapshot = state.homeState.snapshot
    var selectedRoom by remember(snapshot) { mutableStateOf<String?>(null) }
    var addRoom by remember { mutableStateOf(false) }
    var addDevice by remember { mutableStateOf(false) }
    val nodes = snapshot?.nodes.orEmpty().filter { selectedRoom == null || it.roomId == selectedRoom }
    ScreenColumn {
        ScreenHeader(snapshot?.home?.displayName ?: "Ngôi nhà của bạn", "Tổng quan hôm nay")
        if (state.homeState.status == LoadStatus.LOADING) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (snapshot == null) EmptyState(state.homeState.message ?: "Chưa có dữ liệu nhà")
        else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterPill("Tất cả", selectedRoom == null) { selectedRoom = null } }
                items(snapshot.rooms) { room -> FilterPill(room.label, selectedRoom == room.roomId) { selectedRoom = room.roomId } }
                item { FilterPill("＋ Phòng", false) { addRoom = true } }
            }
            SectionTitle("Cảm biến")
            val metrics = latestMetrics(nodes)
            if (metrics.isEmpty()) EmptyState("Chưa có telemetry") else LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(metrics.entries.toList()) { (key, metric) -> MetricCard(key, metric) }
            }
            SectionTitle("Cửa & an ninh")
            nodes.filter { it.nodeType.contains("door") || it.nodeType.contains("lock") }.forEach { node ->
                DoorCard(node) { action -> controller.sendCommand(node.nodeId, action) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("Thiết bị · ${nodes.size}")
                TextButton(onClick = { addDevice = true }) { Text("＋ Thêm") }
            }
            nodes.forEach { node -> DeviceCard(node, { onNode(node.nodeId) },
                { controller.sendCommand(node.nodeId, DeviceActions.TOGGLE) },
                { controller.deleteDevice(node.nodeId, node.label) }) }
            if (selectedRoom != null) {
                OutlinedButton({ controller.deleteRoom(selectedRoom!!) }, Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = SmartHomeColors.Danger)) {
                    Text("Xóa phòng đang chọn")
                }
            }
            OutlinedButton(onClick = onCamera, Modifier.fillMaxWidth()) { Text("Mở trung tâm camera") }
            state.commandMessage?.let { StatusMessage(it, controller::clearCommandMessage) }
        }
    }
    if (addRoom) AddRoomDialog({ addRoom = false }) { controller.addRoom(it); addRoom = false }
    if (addDevice) AddDeviceDialog(snapshot?.rooms.orEmpty(), { addDevice = false }) { label, room, type ->
        controller.addDevice(label, room, type); addDevice = false
    }
}

@Composable
private fun RoomsScreen(state: SmartHomeController.State, onRoom: (String) -> Unit) {
    val snapshot = state.homeState.snapshot
    ScreenColumn {
        ScreenHeader("Phòng", snapshot?.home?.displayName ?: "Chưa chọn nhà")
        if (snapshot?.rooms.isNullOrEmpty()) EmptyState("Chưa có phòng nào")
        snapshot?.rooms.orEmpty().forEach { room ->
            val count = snapshot?.nodes?.count { it.roomId == room.roomId } ?: 0
            SmartCard(Modifier.fillMaxWidth().clickable { onRoom(room.roomId) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(58.dp).clip(RoundedCornerShape(16.dp)).background(roomColor(room.label)),
                        contentAlignment = Alignment.Center) { Text("⌂", fontSize = 28.sp) }
                    Column(Modifier.weight(1f).padding(start = 16.dp)) {
                        Text(room.label, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                        Text("$count thiết bị", color = SmartHomeColors.TextSecondary)
                    }
                    Text("→", fontSize = 22.sp, color = SmartHomeColors.Accent)
                }
            }
        }
    }
}

@Composable
private fun RoomDetailScreen(id: String, state: SmartHomeController.State, controller: SmartHomeController, back: () -> Unit) {
    val snapshot = state.homeState.snapshot
    val room = snapshot?.rooms?.firstOrNull { it.roomId == id }
    val nodes = snapshot?.nodes.orEmpty().filter { it.roomId == id }
    ScreenColumn {
        BackHeader(room?.label ?: "Không tìm thấy phòng", back)
        val camera = nodes.firstOrNull { it.nodeType == "camera" }
        SmartCard(Modifier.fillMaxWidth().height(170.dp), color = Color(0xFF111111)) {
            Text("CAMERA", color = SmartHomeColors.Accent, fontSize = 11.sp)
            Spacer(Modifier.weight(1f))
            Text(camera?.label ?: "Chưa có camera trong phòng này", fontWeight = FontWeight.Bold)
            Text(camera?.status.orEmpty(), color = SmartHomeColors.TextSecondary)
        }
        SectionTitle("Thiết bị · ${nodes.size}")
        nodes.forEach { node -> DeviceCard(node, {},
            { controller.sendCommand(node.nodeId, DeviceActions.TOGGLE) },
            { controller.deleteDevice(node.nodeId, node.label) }) }
    }
}

@Composable
private fun NodeDetailsScreen(id: String, state: SmartHomeController.State, controller: SmartHomeController, back: () -> Unit) {
    val node = state.homeState.snapshot?.nodes?.firstOrNull { it.nodeId == id }
    ScreenColumn {
        BackHeader(node?.label ?: "Node details", back)
        if (node == null) EmptyState("Node không tồn tại hoặc chưa được tải") else {
            SmartCard(Modifier.fillMaxWidth()) {
                DetailLine("Node ID", node.nodeId); DetailLine("Type", node.nodeType)
                DetailLine("Room", node.roomId); DetailLine("Schema", node.schemaVersion.toString())
                DetailLine("Status", node.status); DetailLine("Readings", node.readings.size.toString())
            }
            SectionTitle("Latest telemetry")
            node.latestReading?.metrics?.forEach { (key, value) -> MetricCard(key, value) }
                ?: EmptyState("No telemetry has been received")
            SectionTitle("Device actions")
            if (node.actions.isEmpty()) EmptyState("No actions are declared by this node descriptor")
            node.actions.forEach { action -> OutlinedButton({ controller.sendCommand(node.nodeId, action) }, Modifier.fillMaxWidth()) { Text(action) } }
        }
    }
}

@Composable
private fun CameraScreen(
    state: SmartHomeController.State,
    controller: SmartHomeController,
    bitmap: Bitmap?,
    gateway: String,
    firebase: String,
    back: () -> Unit
) {
    val snapshot = state.homeState.snapshot
    val camera = snapshot?.nodes?.firstOrNull { it.nodeType == "camera" }
    ScreenColumn {
        BackHeader("Camera", back)
        Box(Modifier.fillMaxWidth().aspectRatio(16 / 9f).clip(RoundedCornerShape(20.dp)).background(Color.Black),
            contentAlignment = Alignment.Center) {
            if (bitmap != null) Image(bitmap.asImageBitmap(), "Live camera", Modifier.fillMaxSize())
            else Text("No live RTSP camera feed", color = SmartHomeColors.TextSecondary)
        }
        Text("Gateway: $gateway  ·  Firebase: $firebase", color = SmartHomeColors.TextSecondary, fontSize = 12.sp)
        DetectionToggle("Phát hiện té ngã", camera, DeviceActions.ENABLE_FALL_DETECTION,
            DeviceActions.DISABLE_FALL_DETECTION, controller)
        DetectionToggle("Phát hiện người", camera, DeviceActions.ENABLE_HUMAN_DETECTION,
            DeviceActions.DISABLE_HUMAN_DETECTION, controller)
        SectionTitle("Lịch sử phát hiện")
        if (snapshot?.detectionEvents.isNullOrEmpty()) EmptyState("Chưa có sự kiện phát hiện")
        snapshot?.detectionEvents.orEmpty().forEach { DetectionRow(it) }
    }
}

@Composable
private fun NotificationsScreen(state: SmartHomeController.State) {
    val snapshot = state.homeState.snapshot
    val notifications = buildList<Pair<Long, Pair<String, String>>> {
        snapshot?.accessEvents.orEmpty().forEach { add(it.timestampEpochMs to ("Truy cập ${it.result}" to "${it.roomId} · ${it.credentialType}")) }
        snapshot?.detectionEvents.orEmpty().forEach { add(it.timestampEpochMs to (it.className to "Camera ${it.cameraNodeId} · ${it.roomId}")) }
    }.sortedByDescending { it.first }
    ScreenColumn {
        ScreenHeader("Thông báo", snapshot?.home?.displayName ?: "Sự kiện an ninh")
        if (notifications.isEmpty()) EmptyState("Không có thông báo mới")
        notifications.forEach { (time, detail) -> EventRow(detail.first, detail.second, time) }
    }
}

@Composable
private fun AccountScreen(state: SmartHomeController.State, manage: () -> Unit, logout: () -> Unit) {
    ScreenColumn {
        ScreenHeader("Tài khoản", "Thông tin và cài đặt")
        SmartCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(56.dp).clip(CircleShape).background(SmartHomeColors.AccentSoft),
                    contentAlignment = Alignment.Center) { Text("☺", fontSize = 28.sp) }
                Column(Modifier.padding(start = 14.dp)) {
                    Text(state.authUser?.email ?: "Smart Home User", fontWeight = FontWeight.Bold)
                    Text(state.authUser?.uid.orEmpty(), color = SmartHomeColors.TextSecondary, fontSize = 12.sp)
                }
            }
        }
        SmartCard(Modifier.fillMaxWidth().clickable(onClick = manage)) {
            Text("⌂  Quản lý nhà", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text("Chọn, tạo, tham gia hoặc xóa nhà", color = SmartHomeColors.TextSecondary)
        }
        OutlinedButton(logout, Modifier.fillMaxWidth(), colors = ButtonDefaults.outlinedButtonColors(contentColor = SmartHomeColors.Danger)) {
            Text("Đăng xuất")
        }
    }
}

@Composable
private fun ManageHomesScreen(
    state: SmartHomeController.State,
    controller: SmartHomeController,
    back: () -> Unit,
    create: () -> Unit,
    join: () -> Unit
) {
    var invite by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    ScreenColumn {
        BackHeader("Quản lý nhà", back)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(create, Modifier.weight(1f)) { Text("＋ Tạo nhà") }
            OutlinedButton(join, Modifier.weight(1f)) { Text("Nhập mã mời") }
        }
        state.homes.forEach { home ->
            SmartCard(Modifier.fillMaxWidth().clickable { controller.selectHome(home.homeId) },
                color = if (home.homeId == state.selectedHomeId) SmartHomeColors.AccentSoft else SmartHomeColors.Card) {
                Text(home.name, fontWeight = FontWeight.Bold)
                Text(home.homeId, color = SmartHomeColors.TextSecondary, fontSize = 12.sp)
            }
        }
        OutlinedButton({ controller.createInvite { code, _ -> invite = code } }, Modifier.fillMaxWidth()) { Text("Tạo mã mời") }
        OutlinedButton({ confirmDelete = true }, Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = SmartHomeColors.Danger)) { Text("Xóa nhà đang chọn") }
        invite?.let { StatusMessage("Mã mời: $it") { invite = null } }
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false }, title = { Text("Xóa nhà?") },
        text = { Text("Chỉ chủ nhà mới có thể xóa. Thao tác này không thể hoàn tác.") },
        confirmButton = { TextButton({ controller.deleteSelectedOwnedHome { _, _ -> }; confirmDelete = false }) { Text("Xóa") } },
        dismissButton = { TextButton({ confirmDelete = false }) { Text("Hủy") } }
    )
}

@Composable
private fun CreateHomeScreen(controller: SmartHomeController, back: () -> Unit) {
    var name by remember { mutableStateOf("") }; var type by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }; var error by remember { mutableStateOf<String?>(null) }
    ScreenColumn {
        BackHeader("Tạo nhà mới", back)
        FormField("Tên nhà", name) { name = it }; FormField("Loại nhà", type) { type = it }
        FormField("Địa chỉ", address) { address = it }
        error?.let { Text(it, color = SmartHomeColors.Danger) }
        Button({ controller.createHome(name, type.ifBlank { "Nhà riêng" }, address) { id, message -> if (id != null) back() else error = message } },
            Modifier.fillMaxWidth(), enabled = name.isNotBlank()) { Text("Tạo nhà") }
    }
}

@Composable
private fun JoinHomeScreen(controller: SmartHomeController, back: () -> Unit) {
    var code by remember { mutableStateOf("") }; var error by remember { mutableStateOf<String?>(null) }
    ScreenColumn {
        BackHeader("Tham gia nhà", back)
        Text("Nhập mã mời do chủ nhà gửi cho bạn.", color = SmartHomeColors.TextSecondary)
        FormField("Mã mời", code) { code = it.uppercase(Locale.ROOT) }
        error?.let { Text(it, color = SmartHomeColors.Danger) }
        Button({ controller.redeemInvite(code) { id, message -> if (id != null) back() else error = message } },
            Modifier.fillMaxWidth(), enabled = code.isNotBlank()) { Text("Tham gia") }
    }
}

@Composable
private fun AddRoomDialog(dismiss: () -> Unit, submit: (String) -> Unit) {
    var value by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = dismiss, title = { Text("Thêm phòng") },
        text = { OutlinedTextField(value, { value = it }, label = { Text("Tên phòng") }) },
        confirmButton = { TextButton({ submit(value) }, enabled = value.isNotBlank()) { Text("Lưu") } },
        dismissButton = { TextButton(dismiss) { Text("Hủy") } })
}

@Composable
private fun AddDeviceDialog(rooms: List<RoomSummary>, dismiss: () -> Unit, submit: (String, String, String) -> Unit) {
    var name by remember { mutableStateOf("") }; var room by remember { mutableStateOf(rooms.firstOrNull()?.roomId.orEmpty()) }
    var type by remember { mutableStateOf("light") }
    AlertDialog(onDismissRequest = dismiss, title = { Text("Thêm thiết bị") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FormField("Tên thiết bị", name) { name = it }
            Text("Phòng", color = SmartHomeColors.TextSecondary)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { items(rooms) { FilterPill(it.label, room == it.roomId) { room = it.roomId } } }
            Text("Loại", color = SmartHomeColors.TextSecondary)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(listOf("light", "fan", "air_conditioner", "door_lock", "camera")) { item ->
                    FilterPill(item, type == item) { type = item }
                }
            }
        }
    }, confirmButton = { TextButton({ submit(name, room, type) }, enabled = name.isNotBlank() && room.isNotBlank()) { Text("Thêm") } },
        dismissButton = { TextButton(dismiss) { Text("Hủy") } })
}

@Composable private fun ScreenColumn(content: @Composable ColumnScope.() -> Unit) = LazyColumn(
    Modifier.fillMaxSize().background(SmartHomeColors.Background).padding(horizontal = 20.dp),
    contentPadding = PaddingValues(vertical = 22.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = { item { Column(verticalArrangement = Arrangement.spacedBy(12.dp), content = content) } }
)
@Composable private fun ScreenHeader(title: String, subtitle: String) { Text(title, fontSize = 30.sp, fontWeight = FontWeight.Bold); Text(subtitle, color = SmartHomeColors.TextSecondary) }
@Composable private fun BackHeader(title: String, back: () -> Unit) { Row(verticalAlignment = Alignment.CenterVertically) { TextButton(back) { Text("←") }; Text(title, fontSize = 25.sp, fontWeight = FontWeight.Bold) } }
@Composable private fun SectionTitle(text: String) = Text(text, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
@Composable private fun SmartCard(modifier: Modifier = Modifier, color: Color = SmartHomeColors.Card, content: @Composable ColumnScope.() -> Unit) = Column(modifier.clip(RoundedCornerShape(20.dp)).background(color).border(1.dp, SmartHomeColors.Stroke, RoundedCornerShape(20.dp)).padding(16.dp), content = content)
@Composable private fun EmptyState(text: String) = Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(SmartHomeColors.Card).padding(22.dp), contentAlignment = Alignment.Center) { Text(text, color = SmartHomeColors.TextSecondary) }
@Composable private fun FilterPill(text: String, selected: Boolean, click: () -> Unit) = Text(text, Modifier.clip(CircleShape).background(if (selected) SmartHomeColors.Accent else SmartHomeColors.CardAlt).clickable(onClick = click).padding(horizontal = 14.dp, vertical = 9.dp), color = if (selected) SmartHomeColors.Background else SmartHomeColors.TextPrimary, maxLines = 1)
@Composable private fun FormField(label: String, value: String, change: (String) -> Unit) = OutlinedTextField(value, change, label = { Text(label) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
@Composable private fun MetricCard(key: String, metric: MetricReading) = SmartCard(Modifier.width(150.dp)) { Text(metric.value?.let { String.format(Locale.US, "%.1f %s", it, metric.unit) } ?: "—", fontSize = 22.sp, color = SmartHomeColors.Accent, fontWeight = FontWeight.Bold); Text(key, color = SmartHomeColors.TextSecondary, maxLines = 1) }
@Composable private fun DeviceCard(node: NodeSummary, click: () -> Unit, toggle: () -> Unit, delete: () -> Unit) = SmartCard(Modifier.fillMaxWidth().clickable(onClick = click)) { Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(46.dp).clip(CircleShape).background(SmartHomeColors.AccentSoft), contentAlignment = Alignment.Center) { Text(deviceSymbol(node.nodeType), fontSize = 22.sp) }; Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { Text(node.label, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis); Text("${node.nodeType} · ${node.status}", color = SmartHomeColors.TextSecondary, fontSize = 12.sp) }; Text("×", Modifier.clip(CircleShape).clickable(onClick = delete).padding(10.dp), color = SmartHomeColors.Danger); if (DeviceActions.TOGGLE in node.actions) Switch(false, { toggle() }) } }
@Composable private fun DoorCard(node: NodeSummary, action: (String) -> Unit) = SmartCard(Modifier.fillMaxWidth()) { Row(verticalAlignment = Alignment.CenterVertically) { Text("▣", fontSize = 28.sp, color = SmartHomeColors.Accent); Column(Modifier.weight(1f).padding(start = 12.dp)) { Text(node.label, fontWeight = FontWeight.Bold); Text(node.status, color = SmartHomeColors.TextSecondary) }; Button({ action(if (DeviceActions.UNLOCK in node.actions) DeviceActions.UNLOCK else DeviceActions.LOCK) }) { Text(if (DeviceActions.UNLOCK in node.actions) "Mở" else "Khóa") } } }
@Composable private fun DetailLine(label: String, value: String) = Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label, color = SmartHomeColors.TextSecondary); Text(value, fontWeight = FontWeight.SemiBold) }
@Composable private fun DetectionRow(event: DetectionEvent) = EventRow(event.className, "${event.roomId} · ${(event.confidence * 100).toInt()}%", event.timestampEpochMs)
@Composable private fun EventRow(title: String, detail: String, time: Long) = SmartCard(Modifier.fillMaxWidth()) { Row { Box(Modifier.size(10.dp).clip(CircleShape).background(SmartHomeColors.Accent).align(Alignment.CenterVertically)); Column(Modifier.weight(1f).padding(start = 12.dp)) { Text(title, fontWeight = FontWeight.Bold); Text(detail, color = SmartHomeColors.TextSecondary, fontSize = 12.sp) }; Text(formatTime(time), color = SmartHomeColors.TextSecondary, fontSize = 11.sp) } }
@Composable private fun StatusMessage(text: String, dismiss: () -> Unit = {}) = SmartCard(Modifier.fillMaxWidth(), SmartHomeColors.AccentSoft) { Row { Text(text, Modifier.weight(1f)); if (dismiss !== {}) Text("×", Modifier.clickable(onClick = dismiss)) } }
@Composable private fun DetectionToggle(label: String, node: NodeSummary?, enable: String, disable: String, controller: SmartHomeController) { var checked by remember(node) { mutableStateOf(false) }; SmartCard(Modifier.fillMaxWidth()) { Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(label, fontWeight = FontWeight.Bold); Text(if (node == null) "Không có camera" else "Điều khiển qua gateway", color = SmartHomeColors.TextSecondary, fontSize = 12.sp) }; Switch(checked, { checked = it; node?.let { controller.sendCommand(it.nodeId, if (checked) enable else disable) } }, enabled = node != null && (enable in node.actions || disable in node.actions)) } } }
private fun latestMetrics(nodes: List<NodeSummary>): Map<String, MetricReading> = linkedMapOf<String, MetricReading>().apply { nodes.mapNotNull { it.latestReading }.forEach { putAll(it.metrics) } }
private fun formatTime(value: Long) = if (value <= 0) "—" else SimpleDateFormat("HH:mm · dd/MM", Locale.getDefault()).format(Date(value))
private fun deviceSymbol(type: String) = when { "light" in type -> "✦"; "fan" in type -> "✺"; "camera" in type -> "◉"; "door" in type || "lock" in type -> "▣"; else -> "◆" }
private fun roomColor(label: String) = when { label.contains("bếp", true) || label.contains("kitchen", true) -> Color(0xFF684D2B); label.contains("ngủ", true) || label.contains("bed", true) -> Color(0xFF3B465F); label.contains("tắm", true) || label.contains("bath", true) -> Color(0xFF31545B); else -> SmartHomeColors.AccentSoft }
