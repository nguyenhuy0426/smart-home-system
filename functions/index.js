const functions = require("firebase-functions/v2");
const admin = require("firebase-admin");

admin.initializeApp();

exports.onAccessEvent = functions.firestore
  .onDocumentCreated("homes/{homeId}/events/{eventId}", async (event) => {
    const data = event.data.data();
    const homeId = event.params.homeId;

    if (data.type === "access.denied") {
      const alert = {
        type: "security_alert",
        severity: "high",
        message: `Access denied at ${data.nodeId}. Reason: ${data.reason}`,
        timestamp: admin.firestore.FieldValue.serverTimestamp(),
        relatedEventId: event.params.eventId,
        resolved: false
      };

      await admin.firestore()
        .collection("homes")
        .doc(homeId)
        .collection("alerts")
        .add(alert);
      
      console.log(`Alert created for home ${homeId} for denied access at node ${data.nodeId}`);
    }
  });
