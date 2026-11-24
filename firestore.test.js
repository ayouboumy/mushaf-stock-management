const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_123";
const BOB_UID = "bob_uid_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastTimestamp(offsetMs = 5000) {
  return new Date(Date.now() - offsetMs);
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Global deny: unauthenticated user and top-level unowned collections are rejected", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("products").get());
  await assertFails(unauthDb.collection("products").get());

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("products").get());
  await assertFails(
    aliceDb.collection("products").doc("1001").set({
      localId: 1001,
      userId: ALICE_UID,
      nameArabic: "المصحف المحمدي",
    })
  );
});

test("User profile: owner create/read/update succeeds; cross-user PII read & ghost fields fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = pastTimestamp(10000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      fullName: "عبد الحق المرابط",
      jobTitle: "المكلف بالمستودع والتسليم",
      email: "user@habous.gov.ma",
      phone: "0600000000",
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).get());

  // Partial update on mutable fields only
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      jobTitle: "مندوب الشؤون الإسلامية",
      updatedAt: pastTimestamp(1000),
    })
  );

  // Cross-user PII read/write denied
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({
      fullName: "مخترق",
      updatedAt: pastTimestamp(1000),
    })
  );

  // Ghost field (privilege escalation attempt) rejected
  await assertFails(
    bobDb.collection("users").doc(BOB_UID).set({
      userId: BOB_UID,
      fullName: "مستخدم آخر",
      isAdmin: true,
      createdAt: created,
      updatedAt: created,
    })
  );
});

test("Products: owner create/list/update/delete succeeds; immutable fields, @DocumentId 'id', and future timestamp fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = pastTimestamp(10000);

  const prodRef = aliceDb.collection("users").doc(ALICE_UID).collection("products").doc("1001");
  await assertSucceeds(
    prodRef.set({
      localId: 1001,
      userId: ALICE_UID,
      nameArabic: "المصحف المحمدي برواية ورش",
      nameFrench: "Mushaf Mohammadi",
      category: "مصحف شريف",
      formatType: "عادي",
      unit: "نسخة",
      packageQuantity: 10,
      minimumStock: 50,
      initialStock: 500,
      notes: "طبعة رسمية",
      active: true,
      updatedAtMillis: Date.now() - 10000,
      createdAt: created,
      updatedAt: created,
    })
  );

  // List query on user-scoped subcollection (even when empty for Bob) succeeds for owner
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("products").get());
  await assertSucceeds(bobDb.collection("users").doc(BOB_UID).collection("products").get());

  // Cross-user list/get/update/delete fails
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("products").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("products").doc("1001").get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("products").doc("1001").delete()
  );

  // Valid partial update preserving createdAt, userId, localId
  await assertSucceeds(
    prodRef.update({
      initialStock: 600,
      updatedAtMillis: Date.now(),
      updatedAt: pastTimestamp(1000),
    })
  );

  // Immutable createdAt mutation fails
  await assertFails(
    prodRef.update({
      createdAt: pastTimestamp(500),
      updatedAt: pastTimestamp(500),
    })
  );

  // Extra "id" field (which breaks @DocumentId) fails
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("products").doc("1002").set({
      id: 1002,
      localId: 1002,
      userId: ALICE_UID,
      nameArabic: "صنف بزيادة",
      category: "مصحف شريف",
      formatType: "عادي",
      unit: "نسخة",
      packageQuantity: 10,
      minimumStock: 20,
      initialStock: 100,
      active: true,
      updatedAtMillis: Date.now(),
      createdAt: created,
      updatedAt: created,
    })
  );

  // Future tampered timestamp fails
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("products").doc("1003").set({
      localId: 1003,
      userId: ALICE_UID,
      nameArabic: "صنف بتوقيت مستقبلي",
      category: "مصحف شريف",
      formatType: "عادي",
      unit: "نسخة",
      packageQuantity: 10,
      minimumStock: 20,
      initialStock: 100,
      active: true,
      updatedAtMillis: Date.now(),
      createdAt: new Date(Date.now() + 60000),
      updatedAt: new Date(Date.now() + 60000),
    })
  );

  await assertSucceeds(prodRef.delete());
});

test("Variants, Movements & Destinations: Kotlin payloads (with omitted optional nulls & signed adjustments) succeed", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = pastTimestamp(10000);

  // Variant
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("variants").doc("2001").set({
      localId: 2001,
      userId: ALICE_UID,
      productId: 1001,
      nameArabic: "الفرنسية",
      nameFrench: "Français",
      code: "FR",
      initialStock: 100,
      minimumStock: 20,
      packageQuantity: 10,
      notes: "",
      active: true,
      updatedAtMillis: Date.now(),
      createdAt: created,
      updatedAt: created,
    })
  );

  // Destination
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("destinations").doc("3001").set({
      localId: 3001,
      userId: ALICE_UID,
      name: "مسجد الفتح",
      type: "مسجد",
      commune: "الدريوش",
      province: "الدريوش",
      address: "حي الفتح",
      contactPerson: "الإمام",
      phone: "0600000001",
      notes: "",
      updatedAtMillis: Date.now(),
      createdAt: created,
      updatedAt: created,
    })
  );

  // Movement (omits null optional keys like variantId, destinationId, reversedByMovementId)
  const moveRef = aliceDb.collection("users").doc(ALICE_UID).collection("movements").doc("4001");
  await assertSucceeds(
    moveRef.set({
      localId: 4001,
      userId: ALICE_UID,
      productId: 1001,
      movementType: "ADJUSTMENT",
      quantity: -5,
      packageCount: 1,
      dateMillis: Date.now() - 5000,
      dateFormatted: "2026-11-24",
      reason: "تلف طفيف",
      responsiblePerson: "المكلف بالمستودع",
      referenceNumber: "ADJ-001",
      isReversed: false,
      isDeleted: false,
      updatedAtMillis: Date.now(),
      createdAt: created,
      updatedAt: created,
    })
  );

  // Action: reverse movement
  await assertSucceeds(
    moveRef.update({
      isReversed: true,
      reversedByMovementId: 4002,
      updatedAtMillis: Date.now(),
      updatedAt: pastTimestamp(1000),
    })
  );

  // Invalid movementType fails
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("movements").doc("4003").set({
      localId: 4003,
      userId: ALICE_UID,
      productId: 1001,
      movementType: "INVALID_TYPE",
      quantity: 10,
      packageCount: 1,
      dateMillis: Date.now(),
      isReversed: false,
      isDeleted: false,
      updatedAtMillis: Date.now(),
      createdAt: created,
      updatedAt: created,
    })
  );

  // Health probe create & delete succeeds for owner
  const probeRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("health_probes")
    .doc("health_probe_1");
  await assertSucceeds(
    probeRef.set({
      userId: ALICE_UID,
      probeId: "health_probe_1",
      createdAt: created,
    })
  );
  await assertSucceeds(probeRef.delete());
});
