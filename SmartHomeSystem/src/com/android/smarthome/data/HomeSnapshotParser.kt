package com.android.smarthome.data

object HomeSnapshotParser {
    private val identifier = Regex("[A-Za-z0-9][A-Za-z0-9_-]{0,127}")

    fun parse(homeId: String, uid: String, raw: Any?): HomeSnapshot {
        requireIdentifier("homeId", homeId)
        require(uid.isNotBlank()) { "uid is required" }
        val root = raw.stringMap().orEmpty()
        val member = root["members"].stringMap()?.get(uid)
        var role = roleFromMember(member)
        if (role.isEmpty() && (uid == root.text("owner") || uid == root.text("ownerUid"))) {
            role = "device_admin"
        }
        val displayName = root.text("displayName") ?: root.text("name") ?: homeId
        val nodes = parseNodes(homeId, root["nodes"], root["descriptors"])
            .sortedWith(compareBy<NodeSummary> { it.roomId }.thenBy { it.label }.thenBy { it.nodeId })
        val (access, detections) = parseEvents(root["events"])
        val commands = parseCommands(homeId, root["commandRequests"])
            .sortedByDescending { it.createdAtEpochMs }
        val rooms = parseRooms(root["rooms"], nodes, access, detections)
        return HomeSnapshot(
            HomeSummary(homeId, displayName, role), rooms, nodes,
            access.sortedByDescending { it.timestampEpochMs },
            detections.sortedByDescending { it.timestampEpochMs }, commands
        )
    }

    private fun parseNodes(homeId: String, raw: Any?, descriptorsRaw: Any?): List<NodeSummary> {
        val descriptors = descriptorsRaw.stringMap().orEmpty()
        return raw.stringMap().orEmpty().mapNotNull { (entryId, value) ->
            val node = value.stringMap() ?: return@mapNotNull null
            val nodeId = node.text("nodeId") ?: entryId
            if (!isIdentifier(nodeId)) return@mapNotNull null
            val readings = node["readings"].stringMap().orEmpty().mapNotNull { (key, reading) ->
                parseReading(homeId, nodeId, key, reading)
            }.sortedBy { it.timestampEpochMs }
            val latest = readings.maxByOrNull { it.timestampEpochMs }
            val location = node["location"].stringMap()
            val descriptor = node.text("descriptorHash")?.let { descriptors[it].stringMap() }
            val roomId = node.text("roomId") ?: location?.text("roomId")
                ?: latest?.roomId ?: "unassigned"
            val actionsRaw = node["actions"] ?: descriptor?.get("actions")
            NodeSummary(
                nodeId, homeId, roomId,
                node.text("nodeType") ?: "unknown",
                node.text("label") ?: location?.text("label") ?: nodeId,
                node.number("schemaVersion")?.toInt()
                    ?: node.number("descriptorSchemaVersion")?.toInt()
                    ?: latest?.schemaVersion ?: 1,
                node.text("status") ?: if (latest == null) "no_data" else "reporting",
                parseActions(actionsRaw), readings
            )
        }
    }

    private fun parseActions(raw: Any?): List<String> {
        val values = raw as? List<*> ?: return emptyList()
        return values.mapNotNull { item ->
            when (item) {
                is String -> item
                is Map<*, *> -> item["key"] as? String
                else -> null
            }?.takeIf(::isIdentifier)
        }.distinct()
    }

    private fun parseReading(homeId: String, nodeId: String, key: String, raw: Any?): TelemetryReading? {
        val reading = raw.stringMap() ?: return null
        val readingId = reading.text("readingId") ?: key
        if (!isIdentifier(readingId)) return null
        val metrics = linkedMapOf<String, MetricReading>()
        reading["metrics"].stringMap().orEmpty().forEach { (metricKey, value) ->
            val metric = value.stringMap() ?: return@forEach
            val explicitValid = metric["valid"] as? Boolean
            val quality = metric.text("quality") ?: metric.text("status")
            val finiteValue = metric.number("value")?.toDouble()?.takeIf(Double::isFinite)
            val error = metric.text("error") ?: metric.text("errorCode")
            val validity = when {
                explicitValid == false -> quality ?: "invalid"
                error != null -> quality ?: "error"
                explicitValid == true && finiteValue != null -> quality ?: "valid"
                finiteValue != null && quality == null -> "valid"
                else -> quality ?: "missing"
            }
            metrics[metricKey] = MetricReading(
                metricKey, finiteValue, metric.text("unit").orEmpty(),
                metric.text("source").orEmpty(), validity, error, metric["calibrated"] as? Boolean
            )
        }
        return TelemetryReading(
            readingId, homeId, reading.text("nodeId") ?: nodeId,
            reading.text("roomId") ?: "unassigned",
            reading.number("schemaVersion")?.toInt() ?: 1,
            reading.number("sequence")?.toLong() ?: -1L,
            reading.number("observedAtEpochMs")?.toLong() ?: 0L,
            reading.number("gatewayReceivedAtEpochMs")?.toLong() ?: 0L,
            metrics
        )
    }

    private fun parseEvents(raw: Any?): Pair<List<AccessEvent>, List<DetectionEvent>> {
        val access = mutableListOf<AccessEvent>()
        val detections = mutableListOf<DetectionEvent>()
        raw.stringMap().orEmpty().forEach { (key, value) ->
            val event = value.stringMap() ?: return@forEach
            val eventId = event.text("eventId") ?: key
            if (!isIdentifier(eventId)) return@forEach
            val type = event.text("eventType").orEmpty()
            val timestamp = listOf("observedAtEpochMs", "gatewayReceivedAtEpochMs", "timestamp")
                .firstNotNullOfOrNull { event.number(it)?.toLong()?.takeIf { value -> value > 0L } } ?: 0L
            if (type.startsWith("video.") || event["className"] != null) {
                val bounds = event["boundingBox"].stringMap()
                val box = bounds?.let {
                    val left = it.number("left")?.toDouble()
                    val top = it.number("top")?.toDouble()
                    val right = it.number("right")?.toDouble()
                    val bottom = it.number("bottom")?.toDouble()
                    if (left != null && top != null && right != null && bottom != null &&
                        right >= left && bottom >= top) BoundingBox(left, top, right, bottom) else null
                }
                detections += DetectionEvent(
                    eventId, event.text("nodeId") ?: "unknown", event.text("roomId") ?: "unassigned",
                    timestamp, event.text("className") ?: type.removePrefix("video."),
                    event.number("confidence")?.toDouble() ?: 0.0, box
                )
            } else if (type == "access.attempt" || event["result"] != null) {
                val credentialType = event["credential"].stringMap()?.text("kind")
                    ?: event.text("credentialKind") ?: "unknown"
                access += AccessEvent(
                    eventId, event.text("nodeId") ?: "unknown", event.text("roomId") ?: "unassigned",
                    event.text("result") ?: "unknown", credentialType, timestamp
                )
            }
        }
        return access to detections
    }

    private fun parseCommands(homeId: String, raw: Any?): List<CommandRequest> =
        raw.stringMap().orEmpty().mapNotNull { (key, value) ->
            val request = value.stringMap() ?: return@mapNotNull null
            val requestId = request.text("requestId") ?: key
            if (!isIdentifier(requestId)) return@mapNotNull null
            CommandRequest(
                requestId, request.text("requestedBy").orEmpty(), request.text("homeId") ?: homeId,
                request.text("nodeId").orEmpty(),
                request.text("action") ?: request.text("commandType").orEmpty(),
                request.number("createdAtEpochMs")?.toLong() ?: 0L,
                request.text("status") ?: "pending"
            )
        }

    private fun parseRooms(
        raw: Any?, nodes: List<NodeSummary>, access: List<AccessEvent>, detections: List<DetectionEvent>
    ): List<RoomSummary> {
        val labels = linkedMapOf<String, String>()
        raw.stringMap().orEmpty().forEach { (roomId, value) ->
            if (!isIdentifier(roomId)) return@forEach
            val label = (value as? String)?.takeIf { it.isNotBlank() }
                ?: value.stringMap()?.let { it.text("label") ?: it.text("name") }
                ?: roomId
            labels[roomId] = label
        }
        nodes.forEach { labels.putIfAbsent(it.roomId, it.roomId) }
        access.forEach { labels.putIfAbsent(it.roomId, it.roomId) }
        detections.forEach { labels.putIfAbsent(it.roomId, it.roomId) }
        return labels.map { (id, label) ->
            RoomSummary(id, label, nodes.filter { it.roomId == id }.map { it.nodeId })
        }.sortedBy { it.label }
    }

    private fun roleFromMember(raw: Any?): String {
        val role = when (raw) {
            is String -> raw
            else -> raw.stringMap()?.let { it.text("role") ?: it.text("r") }
        }
        return when (role) {
            null, "" -> ""
            "admin", "owner" -> "device_admin"
            "member" -> "home_member"
            else -> role
        }
    }

    private fun requireIdentifier(name: String, value: String) {
        require(isIdentifier(value)) { "$name is invalid" }
    }

    private fun isIdentifier(value: String?) = value != null && identifier.matches(value)
    private fun Any?.stringMap(): Map<String, Any?>? = (this as? Map<*, *>)?.entries
        ?.filter { it.key is String }?.associate { it.key as String to it.value }
    private fun Map<String, Any?>.text(key: String): String? =
        (this[key] as? String)?.takeIf { it.isNotBlank() }
    private fun Map<String, Any?>.number(key: String): Number? = this[key] as? Number
}
