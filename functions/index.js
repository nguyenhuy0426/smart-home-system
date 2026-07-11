const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const admin = require("firebase-admin");

admin.initializeApp();

const db = admin.database();
const REGION = "asia-southeast1";

function requireAuth(request) {
  if (!request.auth || !request.auth.uid) {
    throw new HttpsError("unauthenticated", "Sign in before using homes.");
  }
  return request.auth.uid;
}

function cleanText(value, maxLength, fallback = "") {
  const text = typeof value === "string" ? value.trim() : fallback;
  return text.slice(0, maxLength);
}

function randomToken(prefix, bytes) {
  const chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
  let token = prefix;
  const random = require("crypto").randomBytes(bytes);
  for (const value of random) {
    token += chars[value % chars.length];
  }
  return token;
}

async function assertHomeAdmin(homeId, uid) {
  const memberSnap = await db.ref(`homes/${homeId}/members/${uid}`).get();
  const member = memberSnap.val();
  const role = member && member.role;
  if (role !== "device_admin" && role !== "gateway_service") {
    throw new HttpsError("permission-denied", "Only a home admin can manage invites.");
  }
}

exports.createHome = onCall({ region: REGION }, async (request) => {
  const uid = requireAuth(request);
  const displayName = cleanText(request.data && request.data.name, 80);
  const homeType = cleanText(request.data && request.data.type, 40, "Nhà riêng") || "Nhà riêng";
  const address = cleanText(request.data && request.data.address, 160);
  if (!displayName) {
    throw new HttpsError("invalid-argument", "Home name is required.");
  }

  const now = Date.now();
  const homeId = `home_${admin.database().ref().push().key.replace(/[^A-Za-z0-9_-]/g, "")}`;
  const home = {
    homeId,
    displayName,
    type: homeType,
    address,
    ownerUid: uid,
    createdAtEpochMs: now,
    members: {
      [uid]: {
        role: "device_admin",
        owner: true,
        joinedAtEpochMs: now
      }
    },
    rooms: {}
  };

  const updates = {};
  updates[`homes/${homeId}`] = home;
  updates[`userHomes/${uid}/${homeId}`] = {
    displayName,
    role: "device_admin",
    owner: true,
    joinedAtEpochMs: now
  };
  await db.ref().update(updates);
  return { homeId, displayName, role: "device_admin" };
});

exports.createHomeInvite = onCall({ region: REGION }, async (request) => {
  const uid = requireAuth(request);
  const homeId = cleanText(request.data && request.data.homeId, 128);
  const role = cleanText(request.data && request.data.role, 32, "home_member") || "home_member";
  if (!/^[A-Za-z0-9][A-Za-z0-9_-]{0,127}$/.test(homeId)) {
    throw new HttpsError("invalid-argument", "Home ID is invalid.");
  }
  if (!["home_member", "access_admin", "device_admin"].includes(role)) {
    throw new HttpsError("invalid-argument", "Invite role is invalid.");
  }
  await assertHomeAdmin(homeId, uid);

  const homeSnap = await db.ref(`homes/${homeId}`).get();
  if (!homeSnap.exists()) {
    throw new HttpsError("not-found", "Home does not exist.");
  }
  const home = homeSnap.val() || {};
  const now = Date.now();
  const expiresAtEpochMs = now + 7 * 24 * 60 * 60 * 1000;
  let code;
  for (let i = 0; i < 5; i += 1) {
    code = randomToken("", 8);
    const existing = await db.ref(`homeInvites/${code}`).get();
    if (!existing.exists()) break;
    code = null;
  }
  if (!code) {
    throw new HttpsError("resource-exhausted", "Could not allocate invite code.");
  }

  await db.ref(`homeInvites/${code}`).set({
    code,
    homeId,
    homeName: home.displayName || homeId,
    role,
    createdBy: uid,
    createdAtEpochMs: now,
    expiresAtEpochMs
  });
  return { code, homeId, role, expiresAtEpochMs };
});

exports.redeemHomeInvite = onCall({ region: REGION }, async (request) => {
  const uid = requireAuth(request);
  const rawCode = cleanText(request.data && request.data.code, 32).toUpperCase();
  if (!/^[A-Z0-9]{4,32}$/.test(rawCode)) {
    throw new HttpsError("invalid-argument", "Invite code is invalid.");
  }

  const inviteRef = db.ref(`homeInvites/${rawCode}`);
  const inviteSnap = await inviteRef.get();
  if (!inviteSnap.exists()) {
    throw new HttpsError("not-found", "Invite code was not found.");
  }
  const invite = inviteSnap.val() || {};
  if (invite.expiresAtEpochMs && invite.expiresAtEpochMs < Date.now()) {
    throw new HttpsError("deadline-exceeded", "Invite code has expired.");
  }
  const homeId = invite.homeId;
  if (!/^[A-Za-z0-9][A-Za-z0-9_-]{0,127}$/.test(homeId)) {
    throw new HttpsError("failed-precondition", "Invite is misconfigured.");
  }

  const homeSnap = await db.ref(`homes/${homeId}`).get();
  if (!homeSnap.exists()) {
    throw new HttpsError("not-found", "Home no longer exists.");
  }
  const home = homeSnap.val() || {};
  const role = ["home_member", "access_admin", "device_admin"].includes(invite.role)
    ? invite.role
    : "home_member";
  const now = Date.now();
  const updates = {};
  updates[`homes/${homeId}/members/${uid}`] = {
    role,
    joinedByInvite: rawCode,
    joinedAtEpochMs: now
  };
  updates[`userHomes/${uid}/${homeId}`] = {
    displayName: home.displayName || invite.homeName || homeId,
    role,
    owner: false,
    joinedAtEpochMs: now
  };
  await db.ref().update(updates);
  return { homeId, displayName: home.displayName || invite.homeName || homeId, role };
});

exports.onAccessEvent = onDocumentCreated("homes/{homeId}/events/{eventId}", async (event) => {
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
