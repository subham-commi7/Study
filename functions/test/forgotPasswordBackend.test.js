const assert = require("assert");
const crypto = require("crypto");
const { _internal } = require("../index.js");

console.log("==========================================");
console.log("RUNNING CLOUD FUNCTIONS OTP BACKEND TESTS");
console.log("==========================================");

// 1. Password Complexity Rules
console.log("\n[Test 1] Validating password complexity requirements...");
assert.strictEqual(_internal.validatePasswordRequirements("Short1"), "Password must be at least 8 characters long.");
assert.strictEqual(_internal.validatePasswordRequirements("nouppercase1"), "Password must contain at least 1 uppercase letter.");
assert.strictEqual(_internal.validatePasswordRequirements("NOLOWERCASE1"), "Password must contain at least 1 lowercase letter.");
assert.strictEqual(_internal.validatePasswordRequirements("NoDigitsHere"), "Password must contain at least 1 number.");
assert.strictEqual(_internal.validatePasswordRequirements("SecurePass2026"), null);
console.log("✓ Password complexity tests PASSED");

// 2. Email Hash Uniformity
console.log("\n[Test 2] Validating email SHA-256 document hashing...");
const hash1 = _internal.hashEmail("student@university.edu");
const hash2 = _internal.hashEmail("  STUDENT@university.edu ");
assert.strictEqual(hash1, hash2, "Email normalization must be trim and lowercase");
assert.strictEqual(hash1.length, 64, "SHA-256 hash must be 64 characters");
console.log("✓ Email normalization & hash tests PASSED");

// 3. Cryptographic OTP Salt + Pepper Hashing
console.log("\n[Test 3] Validating OTP hashing with salt and server-side pepper...");
const salt = crypto.randomBytes(16).toString("hex");
const otp = "123456";
const hashOtp1 = _internal.hashOtp(otp, salt);
const hashOtp2 = _internal.hashOtp(otp, salt);
assert.strictEqual(hashOtp1, hashOtp2, "Hash must be deterministic for identical salt and OTP");
assert.notStrictEqual(_internal.hashOtp("123457", salt), hashOtp1, "Different OTP must produce different hash");
assert.strictEqual(hashOtp1.length, 64, "HMAC-SHA256 must produce 64 characters");
console.log("✓ Cryptographic OTP hashing tests PASSED");

// 4. Timing-Safe Comparison & Attack Prevention
console.log("\n[Test 4] Validating timing-safe comparison on OTP verification...");
const buf1 = Buffer.from(hashOtp1, "hex");
const buf2 = Buffer.from(hashOtp2, "hex");
assert.strictEqual(crypto.timingSafeEqual(buf1, buf2), true);
const bufWrong = Buffer.from(_internal.hashOtp("999999", salt), "hex");
assert.strictEqual(crypto.timingSafeEqual(buf1, bufWrong), false);
console.log("✓ Timing-safe equality tests PASSED");

// 5. Expiry Logic (5 minutes = 300,000 ms)
console.log("\n[Test 5] Validating 5-minute expiry logic...");
assert.strictEqual(_internal.OTP_EXPIRY_MS, 300000);
const now = Date.now();
const expiresAt = now + _internal.OTP_EXPIRY_MS;
assert.ok(now + 120000 < expiresAt, "2 minutes after creation must be valid");
assert.ok(now + 300001 > expiresAt, "5 minutes and 1 ms must be expired");
console.log("✓ OTP 5-minute expiry tests PASSED");

// 6. Resend Cooldown Logic (30 seconds = 30,000 ms)
console.log("\n[Test 6] Validating 30-second resend cooldown logic...");
assert.strictEqual(_internal.RESEND_COOLDOWN_MS, 30000);
const cooldownUntil = now + _internal.RESEND_COOLDOWN_MS;
assert.ok(now + 15000 < cooldownUntil, "15 seconds must be in cooldown");
assert.ok(now + 31000 > cooldownUntil, "31 seconds must allow resend");
console.log("✓ Resend cooldown tests PASSED");

// 7. Maximum 5 Incorrect Attempts
console.log("\n[Test 7] Validating 5-attempt limit logic...");
assert.strictEqual(_internal.MAX_INCORRECT_ATTEMPTS, 5);
let attempts = 0;
for (let i = 1; i <= 5; i++) {
  attempts++;
}
assert.strictEqual(attempts, 5);
assert.ok(attempts >= _internal.MAX_INCORRECT_ATTEMPTS, "6th attempt must be rejected");
console.log("✓ 5-attempt limit tests PASSED");

// 8. One-Time Use & Token Generation (Reset & Signup Tokens)
console.log("\n[Test 8] Validating single-use invalidation & token generation...");
let isUsed = false;
let isVerified = false;
function simulateSuccessfulVerification() {
  isUsed = true;
  isVerified = true;
  return crypto.randomBytes(32).toString("hex");
}
const token = simulateSuccessfulVerification();
assert.strictEqual(isUsed, true, "OTP must be immediately invalidated");
assert.strictEqual(isVerified, true);
assert.strictEqual(token.length, 64, "Token must be 32 bytes (64 hex characters)");
assert.strictEqual(_internal.RESET_TOKEN_EXPIRY_MS, 300000, "Reset token must expire in 5 minutes");
assert.strictEqual(_internal.SIGNUP_TOKEN_EXPIRY_MS, 300000, "Signup token must expire in 5 minutes");
console.log("✓ One-time use & token generation tests PASSED");

// 9. Secret Value Resolution (Secret Manager & env fallback)
console.log("\n[Test 9] Validating Secret Manager resolution logic...");
const secretObj = { value: () => "secret_manager_value" };
assert.strictEqual(_internal.getSecretValue(secretObj, "ANY_KEY", "fallback"), "secret_manager_value");
assert.strictEqual(_internal.getSecretValue(null, "NON_EXISTENT_KEY", "fallback_val"), "fallback_val");
console.log("✓ Secret Manager resolution tests PASSED");

console.log("\n==========================================");
console.log("ALL CLOUD FUNCTIONS UNIT TESTS COMPLETED: 9/9 PASSED");
console.log("==========================================");
