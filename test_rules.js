const { assertFails, assertSucceeds, initializeTestEnvironment } = require("@firebase/rules-unit-testing");
const { readFileSync } = require("fs");

let testEnv;

before(async () => {
  testEnv = await initializeTestEnvironment({
    projectId: "demo-smarthome",
    firestore: {
      rules: readFileSync("firestore.rules", "utf8"),
      host: "127.0.0.1",
      port: 8080,
    },
  });
});

after(async () => {
  await testEnv.cleanup();
});

beforeEach(async () => {
  await testEnv.clearFirestore();
});

describe("Smart Home Firestore Rules", () => {
  it("allows gateway service to write anywhere", async () => {
    const gateway = testEnv.authenticatedContext("gateway_uid", { role: "gateway_service" });
    await assertSucceeds(gateway.firestore().collection("homes").doc("home1").set({ name: "My Home" }));
  });

  it("denies unauthenticated users", async () => {
    const unauth = testEnv.unauthenticatedContext();
    await assertFails(unauth.firestore().collection("homes").doc("home1").get());
  });

  it("allows home members to read their home data", async () => {
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await context.firestore().collection("homes").doc("home1").collection("members").doc("user1").set({ role: "member" });
      await context.firestore().collection("homes").doc("home1").set({ name: "My Home" });
    });

    const alice = testEnv.authenticatedContext("user1");
    await assertSucceeds(alice.firestore().collection("homes").doc("home1").get());
    await assertSucceeds(alice.firestore().collection("homes").doc("home1").collection("nodes").doc("node1").get());
  });

  it("denies non-members from reading home data", async () => {
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await context.firestore().collection("homes").doc("home1").collection("members").doc("user1").set({ role: "member" });
    });

    const bob = testEnv.authenticatedContext("user2");
    await assertFails(bob.firestore().collection("homes").doc("home1").get());
  });

  it("allows members to write command requests", async () => {
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await context.firestore().collection("homes").doc("home1").collection("members").doc("user1").set({ role: "member" });
    });

    const alice = testEnv.authenticatedContext("user1");
    await assertSucceeds(alice.firestore().collection("homes").doc("home1").collection("commandRequests").add({ type: "unlock" }));
  });
});
