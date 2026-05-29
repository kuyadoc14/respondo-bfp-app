const functions = require("firebase-functions");
const admin = require("firebase-admin");
admin.initializeApp();

exports.notifyAdminOnSOS = functions.firestore
  .document("sos_alerts/{alertId}")
  .onCreate(async (snap) => {
    const data = snap.data();

    const lat = data.latitude ? data.latitude.toFixed(5) : "unknown";
    const lng = data.longitude ? data.longitude.toFixed(5) : "unknown";

    const tokensSnap = await admin.firestore()
      .collection("admin_tokens").get();

    const tokens = tokensSnap.docs
      .map((d) => d.data().fcmToken)
      .filter((t) => t && t.length > 0);

    if (tokens.length === 0) {
      console.log("No admin tokens found.");
      return;
    }

    const message = {
      notification: {
        title: "SOS ALERT — BFP",
        body: `Emergency at (${lat}, ${lng}). Respond immediately.`,
      },
      tokens: tokens,
    };

    const response = await admin.messaging().sendEachForMulticast(message);
    console.log(`${response.successCount} notifications sent.`);
  });