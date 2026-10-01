const assert = require("assert");
const http = require("http");

console.log("==================================================");
console.log("RUNNING STUDYMATE FIRESTORE SECURITY RULES TESTS");
console.log("==================================================");

// Emulator configuration
const EMULATOR_HOST = process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8088";
const [host, port] = EMULATOR_HOST.split(":");
const PROJECT_ID = "studymate-c9f21";

/**
 * Creates an unsigned JWT mock token for testing against Firestore Emulator
 */
function createMockJwt(uid, email = null) {
  const header = Buffer.from(JSON.stringify({ alg: "none", typ: "JWT" })).toString("base64url");
  const payload = Buffer.from(JSON.stringify({
    sub: uid,
    user_id: uid,
    email: email || `${uid}@studymate.test`,
    email_verified: true,
    iss: `https://securetoken.google.com/${PROJECT_ID}`,
    aud: PROJECT_ID,
    auth_time: Math.floor(Date.now() / 1000),
    iat: Math.floor(Date.now() / 1000),
    exp: Math.floor(Date.now() / 1000) + 3600
  })).toString("base64url");
  return `${header}.${payload}.`;
}

/**
 * Makes an HTTP request to the Firestore Emulator REST API
 */
function makeFirestoreRequest(method, docPath, data = null, uid = null, email = null) {
  return new Promise((resolve, reject) => {
    const options = {
      hostname: host,
      port: parseInt(port, 10),
      path: `/v1/projects/${PROJECT_ID}/databases/(default)/documents/${docPath}`,
      method: method,
      headers: {
        "Content-Type": "application/json"
      }
    };

    if (uid) {
      options.headers["Authorization"] = `Bearer ${createMockJwt(uid, email)}`;
    }

    const req = http.request(options, (res) => {
      let body = "";
      res.on("data", (chunk) => { body += chunk; });
      res.on("end", () => {
        let json = null;
        try {
          json = body ? JSON.parse(body) : null;
        } catch (_) {}
        resolve({
          statusCode: res.statusCode,
          body: json,
          raw: body
        });
      });
    });

    req.on("error", reject);

    if (data) {
      req.write(JSON.stringify(data));
    }
    req.end();
  });
}

/**
 * Converts a plain JavaScript object to Firestore REST document fields
 */
function toFirestoreFields(obj) {
  const fields = {};
  for (const [key, val] of Object.entries(obj)) {
    if (typeof val === "string") {
      fields[key] = { stringValue: val };
    } else if (typeof val === "number") {
      fields[key] = { integerValue: val.toString() };
    } else if (typeof val === "boolean") {
      fields[key] = { booleanValue: val };
    } else if (Array.isArray(val)) {
      fields[key] = {
        arrayValue: {
          values: val.map(v => ({ stringValue: v }))
        }
      };
    }
  }
  return { fields };
}

async function runTests() {
  const user1 = "student_alpha";
  const user2 = "student_beta";
  const email1 = "alpha@college.edu";
  const email2 = "beta@college.edu";

  console.log("\n[Test 1] Unauthenticated access to private data is denied (401/403)...");
  const unauthRes = await makeFirestoreRequest("GET", `users/${user1}`);
  assert.strictEqual(unauthRes.statusCode, 403, "Unauthenticated read must be blocked (403)");
  console.log("✓ Unauthenticated read blocked");

  console.log("\n[Test 2] Authenticated user can create and read their own profile...");
  const createProfile = await makeFirestoreRequest(
    "PATCH",
    `users/${user1}`,
    toFirestoreFields({
      studentId: "12345678",
      fullName: "Alpha Student",
      email: email1,
      college: "Global Tech",
      course: "B.Tech",
      semester: "6",
      reminderMinutesBefore: 15
    }),
    user1,
    email1
  );
  assert.ok([200, 201].includes(createProfile.statusCode), `Owner profile create must succeed, got ${createProfile.statusCode}: ${createProfile.raw}`);

  const readOwnProfile = await makeFirestoreRequest("GET", `users/${user1}`, null, user1, email1);
  assert.strictEqual(readOwnProfile.statusCode, 200, "Owner must be able to read own profile");
  console.log("✓ Owner can create and read own profile");

  console.log("\n[Test 3] User cannot read another user's private profile or subcollections...");
  const readOtherProfile = await makeFirestoreRequest("GET", `users/${user1}`, null, user2, email2);
  assert.strictEqual(readOtherProfile.statusCode, 403, "Other user must NOT be able to read private profile");

  // Subcollection isolation: attendance_records
  const createAttendance = await makeFirestoreRequest(
    "PATCH",
    `users/${user1}/attendance_records/rec_001`,
    toFirestoreFields({
      subjectId: 101,
      dateString: "2026-10-01",
      status: "PRESENT"
    }),
    user1,
    email1
  );
  assert.ok([200, 201].includes(createAttendance.statusCode), `Owner can record attendance, got ${createAttendance.statusCode}`);

  const readOtherAttendance = await makeFirestoreRequest("GET", `users/${user1}/attendance_records/rec_001`, null, user2, email2);
  assert.strictEqual(readOtherAttendance.statusCode, 403, "Other user must NOT be able to read private attendance records");

  const writeOtherAttendance = await makeFirestoreRequest(
    "PATCH",
    `users/${user1}/attendance_records/rec_002`,
    toFirestoreFields({ subjectId: 101, status: "ABSENT" }),
    user2,
    email2
  );
  assert.strictEqual(writeOtherAttendance.statusCode, 403, "Other user must NOT be able to write to private attendance records");
  console.log("✓ Private profile and subcollections strictly isolated to owner");

  console.log("\n[Test 4] Public profiles allow student ID search without leaking private data...");
  const createPublicProfile = await makeFirestoreRequest(
    "PATCH",
    "public_profiles/12345678",
    toFirestoreFields({
      studentId: "12345678",
      userId: user1,
      fullName: "Alpha Student",
      college: "Global Tech",
      course: "B.Tech"
    }),
    user1,
    email1
  );
  assert.ok([200, 201].includes(createPublicProfile.statusCode), `Owner can create public profile, got ${createPublicProfile.statusCode}`);

  // User 2 can search and read User 1's public directory info
  const searchPublicProfile = await makeFirestoreRequest("GET", "public_profiles/12345678", null, user2, email2);
  assert.strictEqual(searchPublicProfile.statusCode, 200, "Authenticated classmate can look up public profile");

  // User 2 cannot hijack or edit User 1's public profile
  const hijackPublicProfile = await makeFirestoreRequest(
    "PATCH",
    "public_profiles/12345678",
    toFirestoreFields({ fullName: "Hijacked Name", userId: user2 }),
    user2,
    email2
  );
  assert.strictEqual(hijackPublicProfile.statusCode, 403, "Other user cannot modify classmate's public profile");
  console.log("✓ Public directory search allowed for authenticated users and protected against tampering");

  console.log("\n[Test 5] Friendship requests are secured between involved parties...");
  // User 1 sends friend request to User 2
  const createFriendship = await makeFirestoreRequest(
    "PATCH",
    "friendships/friendship_alpha_beta",
    toFirestoreFields({
      userUid: user1,
      friendUid: user2,
      userStudentId: "12345678",
      friendStudentId: "87654321",
      status: "PENDING"
    }),
    user1,
    email1
  );
  assert.ok([200, 201].includes(createFriendship.statusCode), `Friend request creation must succeed, got ${createFriendship.statusCode}`);

  // User 2 (recipient) can read the friend request
  const readFriendshipRecipient = await makeFirestoreRequest("GET", "friendships/friendship_alpha_beta", null, user2, email2);
  assert.strictEqual(readFriendshipRecipient.statusCode, 200, "Recipient can read friend request");

  // Third party (unrelated user) cannot spy on friendship
  const user3 = "student_stranger";
  const readFriendshipStranger = await makeFirestoreRequest("GET", "friendships/friendship_alpha_beta", null, user3, "stranger@college.edu");
  assert.strictEqual(readFriendshipStranger.statusCode, 403, "Stranger cannot read private friendship");

  // Recipient accepts the request
  const acceptFriendship = await makeFirestoreRequest(
    "PATCH",
    "friendships/friendship_alpha_beta",
    toFirestoreFields({
      userUid: user1,
      friendUid: user2,
      userStudentId: "12345678",
      friendStudentId: "87654321",
      status: "ACCEPTED"
    }),
    user2,
    email2
  );
  assert.ok([200, 201].includes(acceptFriendship.statusCode), "Recipient can update status to ACCEPTED");
  console.log("✓ Friendships secured between involved parties only");

  console.log("\n[Test 6] Shared documents are accessible only to sender and recipient...");
  const createSharedDoc = await makeFirestoreRequest(
    "PATCH",
    "shared_documents/doc_lecture_notes",
    toFirestoreFields({
      senderUid: user1,
      recipientUid: user2,
      senderStudentId: "12345678",
      recipientStudentId: "87654321",
      documentTitle: "Unit 3 Chemistry Notes",
      documentType: "NOTES"
    }),
    user1,
    email1
  );
  assert.ok([200, 201].includes(createSharedDoc.statusCode), `Shared document creation must succeed, got ${createSharedDoc.statusCode}`);

  const readSharedDocRecipient = await makeFirestoreRequest("GET", "shared_documents/doc_lecture_notes", null, user2, email2);
  assert.strictEqual(readSharedDocRecipient.statusCode, 200, "Recipient can access shared document");

  const readSharedDocStranger = await makeFirestoreRequest("GET", "shared_documents/doc_lecture_notes", null, user3, "stranger@college.edu");
  assert.strictEqual(readSharedDocStranger.statusCode, 403, "Third party cannot access shared document");
  console.log("✓ Shared documents protected");

  console.log("\n[Test 7] Syllabus catalog is readable by all authenticated students...");
  const createSyllabus = await makeFirestoreRequest(
    "PATCH",
    "syllabuses/cs_algorithms",
    toFirestoreFields({
      course: "Computer Science",
      semester: "4",
      subjectName: "Design & Analysis of Algorithms",
      authorUid: user1
    }),
    user1,
    email1
  );
  assert.ok([200, 201].includes(createSyllabus.statusCode), `Author can create syllabus, got ${createSyllabus.statusCode}`);

  const readSyllabusOther = await makeFirestoreRequest("GET", "syllabuses/cs_algorithms", null, user2, email2);
  assert.strictEqual(readSyllabusOther.statusCode, 200, "Any authenticated student can read syllabus catalog");
  console.log("✓ Syllabus catalog readable by students and editable by author");

  console.log("\n[Test 8] OTP collections are strictly blocked from all client SDKs...");
  const tryReadOtp = await makeFirestoreRequest("GET", "password_reset_otps/hash123", null, user1, email1);
  assert.strictEqual(tryReadOtp.statusCode, 403, "Client read of password_reset_otps must be blocked");

  const tryWriteOtp = await makeFirestoreRequest(
    "PATCH",
    "signup_otps/hash123",
    toFirestoreFields({ otp: "123456" }),
    user1,
    email1
  );
  assert.strictEqual(tryWriteOtp.statusCode, 403, "Client write of signup_otps must be blocked");
  console.log("✓ OTP collections completely inaccessible to client SDKs");

  console.log("\n==================================================");
  console.log("ALL FIRESTORE SECURITY RULES TESTS PASSED (8/8)!");
  console.log("==================================================");
}

runTests().catch((err) => {
  console.error("Test failed with error:", err);
  process.exit(1);
});
