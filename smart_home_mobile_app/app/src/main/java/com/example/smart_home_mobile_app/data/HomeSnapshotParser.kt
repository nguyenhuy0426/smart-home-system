package com.example.smart_home_mobile_app.data

object HomeSnapshotParser {
    fun parse(homeId: String, uid: String, raw: Any?): HomeSnapshot {
        requireIdentifier("homeId", homeId)
        require(uid.isNotBlank()) { "uid is required" }
        val root = raw.asStringMap() ?: emptyMap()
        val member = root["members"].asStringMap()?.get(uid).asStringMap()
        val role = member?.string("role").orEmpty()
        val displayName = root.string("displayName")
            ?: root.string("name")
            ?: homeId

        val nodes = parseNodes(homeId, root["nodes"], root["descriptors"])
        val accessEvents = ArrayList<AccessEvent>()
        val detectionEvents = ArrayList<DetectionEvent>()
        parseEvents(root["events"], accessEvents, detectionEvents)
        val commands = parseCommands(homeId, root["commandRequests"])
        val rooms = parseRooms(root["rooms"], nodes, accessEvents, detectionEvents)

        return HomeSnapshot(
            home = HomeSummary(homeId, displayName, role),
            rooms = rooms,
            nodes = nodes.sortedWith(compareBy(NodeSummary::roomId, NodeSummary::label, NodeSummary::nodeId)),
            accessEvents = accessEvents.sortedByDescending(AccessEvent::timestampEpochMs),
            detectionEvents = detectionEvents.sortedByDescending(DetectionEvent::timestampEpochMs),
            commandRequests = commands.sortedByDescending(CommandRequest::createdAtEpochMs),
        )
    }

    private fun parseNodes(homeId: String, raw: Any?, descriptorsRaw: Any?): List<NodeSummary> {
        val descriptors = descriptorsRaw.asStringMap().orEmpty()
        return raw.asStringMap().orEmpty().mapNotNull { (nodeKey, nodeRaw) ->
            val node = nodeRaw.asStringMap() ?: return@mapNotNull null
            val nodeId = node.string("nodeId") ?: nodeKey
            if (!isIdentifier(nodeId)) return@mapNotNull null
            val readings = node["readings"].asStringMap().orEmpty().mapNotNull { (readingKey, readingRaw) ->
                parseReading(homeId, nodeId, readingKey, readingRaw)
            }.sortedBy(TelemetryReading::timestampEpochMs)
            val latest = readings.maxByOrNull(TelemetryReading::timestampEpochMs)
            val location = node["location"].asStringMap()
            val descriptor = node.string("descriptorHash")?.let { descriptors[it].asStringMap() }
            NodeSummary(
                nodeId = nodeId,
                homeId = homeId,
                roomId = node.string("roomId")
                    ?: location?.string("roomId")
                    ?: latest?.roomId
                    ?: "unassigned",
                nodeType = node.string("nodeType") ?: "unknown",
                label = node.string("label")
                    ?: location?.string("label")
                    ?: nodeId,
                schemaVersion = node.int("schemaVersion")
                    ?: node.int("descriptorSchemaVersion")
                    ?: latest?.schemaVersion
                    ?: 1,
                status = node.string("status") ?: if (latest == null) "no_data" else "reporting",
                actions = parseActions(node["actions"] ?: descriptor?.get("actions")),
                readings = readings,
            )
        }
    }

    private fun parseActions(raw: Any?): List<String> {
        val list = raw as? List<*> ?: return emptyList()
        return list.mapNotNull { actionRaw ->
            when (actionRaw) {
                is String -> actionRaw
                is Map<*, *> -> actionRaw["key"] as? String
                else -> null
            }
        }.filter(::isIdentifier).distinct()
    }

    private fun parseReading(
        homeId: String,
        nodeId: String,
        readingKey: String,
        raw: Any?,
    ): TelemetryReading? {
        val reading = raw.asStringMap() ?: return null
        val readingId = reading.string("readingId") ?: readingKey
        if (!isIdentifier(readingId)) return null
        val metrics = reading["metrics"].asStringMap().orEmpty().mapNotNull { (key, metricRaw) ->
            val metric = metricRaw.asStringMap() ?: return@mapNotNull null
            val explicitValid = metric.boolean("valid")
            val quality = metric.string("quality") ?: metric.string("status")
            val value = metric.double("value")?.takeIf(Double::isFinite)
            val error = metric.string("error") ?: metric.string("errorCode")
            val validity = when {
                explicitValid == false -> quality ?: "invalid"
                error != null -> quality ?: "error"
                explicitValid == true && value != null -> quality ?: "valid"
                value != null && quality == null -> "valid"
                else -> quality ?: "missing"
            }
            key to MetricReading(
                key = key,
                value = value,
                unit = metric.string("unit").orEmpty(),
                source = metric.string("source").orEmpty(),
                validity = validity,
                error = error,
                calibrated = metric.boolean("calibrated"),
            )
        }.toMap()
        return TelemetryReading(
            readingId = readingId,
            homeId = homeId,
            nodeId = reading.string("nodeId") ?: nodeId,
            roomId = reading.string("roomId") ?: "unassigned",
            schemaVersion = reading.int("schemaVersion") ?: 1,
            sequence = reading.long("sequence") ?: -1L,
            observedAtEpochMs = reading.long("observedAtEpochMs") ?: 0L,
            gatewayReceivedAtEpochMs = reading.long("gatewayReceivedAtEpochMs") ?: 0L,
            metrics = metrics,
        )
    }

    private fun parseEvents(
        raw: Any?,
        accessEvents: MutableList<AccessEvent>,
        detectionEvents: MutableList<DetectionEvent>,
    ) {
        raw.asStringMap().orEmpty().forEach { (eventKey, eventRaw) ->
            val event = eventRaw.asStringMap() ?: return@forEach
            val eventId = event.string("eventId") ?: eventKey
            if (!isIdentifier(eventId)) return@forEach
            val type = event.string("eventType").orEmpty()
            // Nodes send observedAtEpochMs = 0/null before their clock syncs;
            // fall through to the gateway receive time instead of epoch zero.
            val timestamp = event.long("observedAtEpochMs")?.takeIf { it > 0L }
                ?: event.long("gatewayReceivedAtEpochMs")?.takeIf { it > 0L }
                ?: event.long("timestamp")?.takeIf { it > 0L }
                ?: 0L
            if (type.startsWith("video.") || event["className"] != null) {
                val box = event["boundingBox"].asStringMap()?.let { bounds ->
                    val left = bounds.double("left")
                    val top = bounds.double("top")
                    val right = bounds.double("right")
                    val bottom = bounds.double("bottom")
                    if (left != null && top != null && right != null && bottom != null &&
                        right >= left && bottom >= top
                    ) BoundingBox(left, top, right, bottom) else null
                }
                detectionEvents += DetectionEvent(
                    eventId = eventId,
                    cameraNodeId = event.string("nodeId") ?: "unknown",
                    roomId = event.string("roomId") ?: "unassigned",
                    timestampEpochMs = timestamp,
                    className = event.string("className") ?: type.removePrefix("video."),
                    confidence = event.double("confidence") ?: 0.0,
                    boundingBox = box,
                )
            } else if (type == "access.attempt" || event["result"] != null) {
                val credential = event["credential"].asStringMap()
                accessEvents += AccessEvent(
                    eventId = eventId,
                    nodeId = event.string("nodeId") ?: "unknown",
                    roomId = event.string("roomId") ?: "unassigned",
                    result = event.string("result") ?: "unknown",
                    credentialType = credential?.string("kind")
                        ?: event.string("credentialKind")
                        ?: "unknown",
                    timestampEpochMs = timestamp,
                )
            }
        }
    }

    private fun parseCommands(homeId: String, raw: Any?): List<CommandRequest> {
        return raw.asStringMap().orEmpty().mapNotNull { (requestKey, requestRaw) ->
            val request = requestRaw.asStringMap() ?: return@mapNotNull null
            val requestId = request.string("requestId") ?: requestKey
            if (!isIdentifier(requestId)) return@mapNotNull null
            CommandRequest(
                requestId = requestId,
                requestedBy = request.string("requestedBy").orEmpty(),
                homeId = request.string("homeId") ?: homeId,
                targetNodeId = request.string("nodeId").orEmpty(),
                commandType = request.string("action") ?: request.string("commandType").orEmpty(),
                createdAtEpochMs = request.long("createdAtEpochMs") ?: 0L,
                status = request.string("status") ?: "pending",
            )
        }
    }

    private fun parseRooms(
        raw: Any?,
        nodes: List<NodeSummary>,
        accessEvents: List<AccessEvent>,
        detectionEvents: List<DetectionEvent>,
    ): List<RoomSummary> {
        val labels = LinkedHashMap<String, String>()
        raw.asStringMap().orEmpty().forEach { (roomId, roomRaw) ->
            if (isIdentifier(roomId)) {
                val room = roomRaw.asStringMap()
                labels[roomId] = room?.string("label") ?: room?.string("name") ?: roomId
            }
        }
        nodes.forEach { labels.putIfAbsent(it.roomId, it.roomId) }
        accessEvents.forEach { labels.putIfAbsent(it.roomId, it.roomId) }
        detectionEvents.forEach { labels.putIfAbsent(it.roomId, it.roomId) }
        return labels.map { (roomId, label) ->
            RoomSummary(roomId, label, nodes.filter { it.roomId == roomId }.map(NodeSummary::nodeId))
        }.sortedBy(RoomSummary::label)
    }

    private fun requireIdentifier(name: String, value: String) {
        require(isIdentifier(value)) { "$name is invalid" }
    }

    private fun isIdentifier(value: String): Boolean =
        value.matches(Regex("[A-Za-z0-9][A-Za-z0-9_-]{0,127}"))

    private fun Any?.asStringMap(): Map<String, Any?>? {
        val raw = this as? Map<*, *> ?: return null
        return raw.entries.mapNotNull { (key, value) ->
            (key as? String)?.let { it to value }
        }.toMap(LinkedHashMap())
    }

    private fun Map<String, Any?>.string(key: String): String? =
        (this[key] as? String)?.takeIf(String::isNotBlank)

    private fun Map<String, Any?>.long(key: String): Long? =
        (this[key] as? Number)?.toLong()

    private fun Map<String, Any?>.int(key: String): Int? =
        (this[key] as? Number)?.toInt()

    private fun Map<String, Any?>.double(key: String): Double? =
        (this[key] as? Number)?.toDouble()

    private fun Map<String, Any?>.boolean(key: String): Boolean? =
        this[key] as? Boolean
}
