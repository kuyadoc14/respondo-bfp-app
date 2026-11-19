const functions = require("firebase-functions");
const admin = require("firebase-admin");

admin.initializeApp();

/**
 * Sanitizes strings for notification payloads to prevent injection or formatting abuse.
 */
function sanitizeString(str, maxLength = 100) {
  if (typeof str !== "string") return "";
  return str.replace(/[\r\n\t]/g, " ").replace(/[<>]/g, "").trim().substring(0, maxLength);
}

/**
 * Triggered on new SOS alert creation.
 * Validates data integrity, enforces anti-spam rate limiting,
 * dispatches notifications to verified admin devices, and cleans up dead FCM tokens.
 */
exports.notifyAdminOnSOS = functions.firestore
  .document("sos_alerts/{alertId}")
  .onCreate(async (snap, context) => {
    const data = snap.data();
    if (!data) return;

    // 1. Data Integrity & Validation Checks
    if (data.status !== "active") {
      console.log(`Alert ${context.params.alertId} status is '${data.status}', skipping notification.`);
      return;
    }

    const lat = typeof data.latitude === "number" && !isNaN(data.latitude) ? data.latitude : null;
    const lng = typeof data.longitude === "number" && !isNaN(data.longitude) ? data.longitude : null;

    if (lat === null || lng === null || lat < -90 || lat > 90 || lng < -180 || lng > 180) {
      console.warn(`Alert ${context.params.alertId} has invalid GPS coordinates (${lat}, ${lng}).`);
    }

    // 2. Anti-Abuse Rate Limiting
    // If the same userId generated > 5 alerts within the last 10 minutes, flag as spam
    if (data.userId && typeof data.userId === "string") {
      const tenMinutesAgo = new Date(Date.now() - 10 * 60 * 1000);
      try {
        const recentAlertsSnap = await admin.firestore()
          .collection("sos_alerts")
          .where("userId", "==", data.userId)
          .where("timestamp", ">=", admin.firestore.Timestamp.fromDate(tenMinutesAgo))
          .get();

        if (recentAlertsSnap.size > 5) {
          console.warn(`Abuse detected: User ${data.userId} sent ${recentAlertsSnap.size} alerts in 10 minutes. Flagging as spam.`);
          await snap.ref.update({
            status: "flagged_spam",
            spamReason: "Exceeded rate limit of 5 alerts per 10 minutes",
            flaggedAt: admin.firestore.FieldValue.serverTimestamp()
          });
          return;
        }
      } catch (rateLimitErr) {
        console.error("Rate limit check error:", rateLimitErr);
      }
    }

    // 3. Fetch Registered BFP Admin FCM Tokens
    const tokensSnap = await admin.firestore()
      .collection("admin_tokens")
      .get();

    const tokenMap = new Map(); // token -> docId
    tokensSnap.docs.forEach((doc) => {
      const token = doc.data().fcmToken;
      if (token && typeof token === "string" && token.trim().length > 0) {
        tokenMap.set(token.trim(), doc.id);
      }
    });

    const tokens = Array.from(tokenMap.keys());
    if (tokens.length === 0) {
      console.log("No registered admin tokens found for notification.");
      return;
    }

    // 4. Construct Secure Payload
    const latStr = lat !== null ? lat.toFixed(5) : "Unknown";
    const lngStr = lng !== null ? lng.toFixed(5) : "Unknown";
    const incidentType = sanitizeString(data.accidentType || "Emergency Incident", 40);
    const reporterName = sanitizeString(data.reporterName || "", 40);

    let notificationBody = `Emergency at (${latStr}, ${lngStr}). Respond immediately.`;
    if (reporterName) {
      notificationBody = `Reported by ${reporterName}: ${incidentType} at (${latStr}, ${lngStr}).`;
    }

    const message = {
      notification: {
        title: "🚨 BFP EMERGENCY SOS ALERT",
        body: notificationBody,
      },
      data: {
        alertId: String(context.params.alertId),
        latitude: String(latStr),
        longitude: String(lngStr),
        accidentType: incidentType,
        click_action: "FLUTTER_NOTIFICATION_CLICK",
      },
      tokens: tokens,
    };

    // 5. Send Multicast and Prune Invalid / Stale Tokens
    try {
      const response = await admin.messaging().sendEachForMulticast(message);
      console.log(`Dispatched SOS to ${response.successCount}/${tokens.length} devices.`);

      if (response.failureCount > 0) {
        const deadDocIds = [];
        response.responses.forEach((resp, idx) => {
          if (!resp.success) {
            const errCode = resp.error?.code;
            if (
              errCode === "messaging/invalid-registration-token" ||
              errCode === "messaging/registration-token-not-registered"
            ) {
              const deadToken = tokens[idx];
              const docId = tokenMap.get(deadToken);
              if (docId) deadDocIds.push(docId);
            }
          }
        });

        // Prune dead tokens in batches of up to 500
        if (deadDocIds.length > 0) {
          console.log(`Pruning ${deadDocIds.length} invalid admin token(s) from database...`);
          const batch = admin.firestore().batch();
          deadDocIds.slice(0, 500).forEach((id) => {
            batch.delete(admin.firestore().collection("admin_tokens").document(id));
          });
          await batch.commit();
        }
      }
    } catch (sendErr) {
      console.error("Failed to multicast notification to admin devices:", sendErr);
    }
  });
