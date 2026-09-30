const { onRequest } = require("firebase-functions/v2/https");
const { setGlobalOptions } = require("firebase-functions/v2");
const { defineSecret } = require("firebase-functions/params");
const admin = require("firebase-admin");
const nodemailer = require("nodemailer");
const crypto = require("crypto");

// Set default region to us-central1 for all Cloud Functions
setGlobalOptions({ region: "us-central1" });

// Define Google Cloud Secret Manager secrets
const smtpPass = defineSecret("SMTP_PASS");
const smtpUser = defineSecret("SMTP_USER");
const otpPepperSecret = defineSecret("OTP_PEPPER_SECRET");

// Common options with Secret Manager bindings for all OTP functions
const functionOptions = {
  cors: true,
  secrets: [smtpPass, smtpUser, otpPepperSecret]
};

// Initialize Firebase Admin SDK
if (!admin.apps.length) {
  admin.initializeApp();
}

const db = admin.firestore();

// Configuration constants
const OTP_LENGTH = 6;
const OTP_EXPIRY_MS = 5 * 60 * 1000; // 5 minutes
const RESEND_COOLDOWN_MS = 30 * 1000; // 30 seconds
const MAX_INCORRECT_ATTEMPTS = 5;
const RESET_TOKEN_EXPIRY_MS = 5 * 60 * 1000; // 5 minutes
const SIGNUP_TOKEN_EXPIRY_MS = 5 * 60 * 1000; // 5 minutes

const SENDER_EMAIL = process.env.SENDER_EMAIL || "stumatehelp@gmail.com";
const SENDER_DISPLAY_NAME = process.env.SENDER_DISPLAY_NAME || "StudyMate";

/**
 * Safely resolves a Secret Manager secret value, falling back to process.env or fallback.
 */
function getSecretValue(secretParam, envKey, fallback = "") {
  try {
    if (secretParam && typeof secretParam.value === "function") {
      const val = secretParam.value();
      if (val) return val;
    }
  } catch (_) {}
  return process.env[envKey] || fallback;
}

/**
 * Creates email transporter with TLS/SMTP settings.
 * Uses Secret Manager SMTP_USER & SMTP_PASS if provided.
 */
function createTransporter() {
  const user = getSecretValue(smtpUser, "SMTP_USER", "stumatehelp@gmail.com");
  const pass = getSecretValue(smtpPass, "SMTP_PASS", "");

  if (!pass) {
    console.warn("SMTP_PASS secret is not configured. Outgoing email may fail.");
  }

  return nodemailer.createTransport({
    host: process.env.SMTP_HOST || "smtp.gmail.com",
    port: parseInt(process.env.SMTP_PORT || "465", 10),
    secure: (process.env.SMTP_SECURE !== "false"),
    auth: {
      user: user,
      pass: pass,
    },
  });
}

/**
 * Computes constant-length SHA-256 hash of an email for Firestore doc ID.
 */
function hashEmail(email) {
  return crypto.createHash("sha256").update(email.trim().toLowerCase()).digest("hex");
}

/**
 * Computes SHA-256 HMAC of OTP combined with salt and server-side Secret Manager pepper.
 */
function hashOtp(otp, salt) {
  const pepper = getSecretValue(otpPepperSecret, "OTP_PEPPER_SECRET", "studymate_secure_otp_pepper_2026");
  return crypto.createHmac("sha256", pepper).update(`${salt}:${otp}`).digest("hex");
}

/**
 * Validates password complexity:
 * - Minimum 8 characters
 * - At least 1 uppercase letter
 * - At least 1 lowercase letter
 * - At least 1 number
 */
function validatePasswordRequirements(password) {
  if (!password || password.length < 8) {
    return "Password must be at least 8 characters long.";
  }
  if (!/[A-Z]/.test(password)) {
    return "Password must contain at least 1 uppercase letter.";
  }
  if (!/[a-z]/.test(password)) {
    return "Password must contain at least 1 lowercase letter.";
  }
  if (!/[0-9]/.test(password)) {
    return "Password must contain at least 1 number.";
  }
  return null;
}

// ============================================================================
// 1. FORGOT PASSWORD FLOW
// ============================================================================

/**
 * 1.1 REQUEST PASSWORD RESET OTP
 * Generates 6-digit random OTP, hashes it, stores in Firestore, sends professional email.
 */
exports.requestPasswordResetOtp = onRequest(functionOptions, async (req, res) => {
  if (req.method !== "POST") {
    return res.status(405).json({ success: false, error: "Method Not Allowed" });
  }

  const { email } = req.body || {};
  if (!email || typeof email !== "string" || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim())) {
    return res.status(400).json({ success: false, error: "Please enter a valid email address." });
  }

  const normalizedEmail = email.trim().toLowerCase();
  const emailDocId = hashEmail(normalizedEmail);
  const docRef = db.collection("password_reset_otps").document(emailDocId);

  try {
    // Check if user exists in Firebase Authentication
    let firebaseUser;
    try {
      firebaseUser = await admin.auth().getUserByEmail(normalizedEmail);
    } catch (e) {
      if (e.code === "auth/user-not-found") {
        // Account enumeration protection: Return generic neutral response
        return res.status(200).json({
          success: true,
          message: "If an account exists for this email address, a verification code will be sent.",
          cooldownSeconds: 30,
          expiresInSeconds: 300
        });
      }
      throw e;
    }

    // Check resend cooldown (30 seconds)
    const now = Date.now();
    const docSnap = await docRef.get();
    if (docSnap.exists) {
      const existingData = docSnap.data();
      if (existingData.cooldownUntil && now < existingData.cooldownUntil) {
        const remainingSeconds = Math.ceil((existingData.cooldownUntil - now) / 1000);
        return res.status(429).json({
          success: false,
          error: `Please wait ${remainingSeconds} seconds before requesting a new code.`,
          cooldownRemainingSeconds: remainingSeconds
        });
      }
    }

    // Generate random 6-digit OTP (100000 - 999999)
    const rawOtp = crypto.randomInt(100000, 1000000).toString();
    const salt = crypto.randomBytes(16).toString("hex");
    const otpHash = hashOtp(rawOtp, salt);

    const expiresAt = now + OTP_EXPIRY_MS;
    const cooldownUntil = now + RESEND_COOLDOWN_MS;

    // Save record in Firestore (never plaintext OTP)
    await docRef.set({
      uid: firebaseUser.uid,
      email: normalizedEmail,
      salt: salt,
      otpHash: otpHash,
      createdAt: now,
      expiresAt: expiresAt,
      cooldownUntil: cooldownUntil,
      attempts: 0,
      maxAttempts: MAX_INCORRECT_ATTEMPTS,
      isUsed: false,
      isVerified: false,
      resetToken: null,
      tokenExpiresAt: null,
      passwordResetCompleted: false
    });

    // Send professional email with exact required template
    const studentName = firebaseUser.displayName || "Student";
    const emailSubject = "StudyMate Password Reset OTP";
    const emailBody = `Hello ${studentName},

We received a request to reset the password for your StudyMate account.

Your verification code is:

${rawOtp}

This code will expire in 5 minutes.

If you did not request a password reset, you can safely ignore this email.

For your security, never share this code with anyone.

Regards,
StudyMate Team

Student Academic Intelligence`;

    const transporter = createTransporter();
    await transporter.sendMail({
      from: `"${SENDER_DISPLAY_NAME}" <${SENDER_EMAIL}>`,
      to: normalizedEmail,
      subject: emailSubject,
      text: emailBody
    });

    return res.status(200).json({
      success: true,
      message: "If an account exists for this email address, a verification code will be sent.",
      cooldownSeconds: 30,
      expiresInSeconds: 300
    });
  } catch (error) {
    console.error("Error in requestPasswordResetOtp:", error);
    return res.status(500).json({
      success: false,
      error: "Unable to send verification code. Please check backend email configuration."
    });
  }
});

/**
 * 1.2 VERIFY PASSWORD RESET OTP
 * Verifies 6-digit code against server hash, checks expiry & attempt limits,
 * immediately invalidates OTP and generates a single-use password reset token.
 */
exports.verifyPasswordResetOtp = onRequest(functionOptions, async (req, res) => {
  if (req.method !== "POST") {
    return res.status(405).json({ success: false, error: "Method Not Allowed" });
  }

  const { email, otp } = req.body || {};
  if (!email || !otp) {
    return res.status(400).json({ success: false, error: "Email and verification code are required." });
  }

  const cleanOtp = String(otp).trim();
  if (cleanOtp.length !== OTP_LENGTH || !/^\d{6}$/.test(cleanOtp)) {
    return res.status(400).json({ success: false, error: "Verification code must be exactly 6 digits." });
  }

  const normalizedEmail = email.trim().toLowerCase();
  const emailDocId = hashEmail(normalizedEmail);
  const docRef = db.collection("password_reset_otps").document(emailDocId);

  try {
    const docSnap = await docRef.get();
    if (!docSnap.exists) {
      return res.status(400).json({ success: false, error: "No active verification request found. Please request a new code." });
    }

    const data = docSnap.data();
    const now = Date.now();

    // Check if OTP was already used
    if (data.isUsed) {
      return res.status(400).json({ success: false, error: "This verification code has already been used. Please request a new code." });
    }

    // Check expiry (5 minutes)
    if (now > data.expiresAt) {
      return res.status(400).json({ success: false, error: "Verification code has expired. Please request a new code." });
    }

    // Check attempt limit (5 attempts)
    if (data.attempts >= data.maxAttempts) {
      return res.status(429).json({
        success: false,
        error: "Maximum verification attempts exceeded. Please request a new code."
      });
    }

    // Verify OTP using constant-time comparison
    const computedHash = hashOtp(cleanOtp, data.salt);
    const computedBuffer = Buffer.from(computedHash, "hex");
    const storedBuffer = (typeof data.otpHash === "string") ? Buffer.from(data.otpHash, "hex") : Buffer.alloc(0);
    const hashMatches = (computedBuffer.length === storedBuffer.length) && crypto.timingSafeEqual(computedBuffer, storedBuffer);

    if (!hashMatches) {
      const newAttempts = (data.attempts || 0) + 1;
      const remaining = data.maxAttempts - newAttempts;
      await docRef.update({ attempts: newAttempts });

      if (remaining <= 0) {
        return res.status(429).json({
          success: false,
          error: "Maximum verification attempts exceeded. Please request a new code."
        });
      }

      return res.status(400).json({
        success: false,
        error: `Incorrect verification code. ${remaining} attempt${remaining === 1 ? "" : "s"} remaining.`
      });
    }

    // OTP Verified! Immediately invalidate OTP and generate a short-lived reset token
    const resetToken = crypto.randomBytes(32).toString("hex");
    const tokenExpiresAt = now + RESET_TOKEN_EXPIRY_MS;

    await docRef.update({
      isUsed: true, // One-time use: immediately invalidated!
      isVerified: true,
      resetToken: resetToken,
      tokenExpiresAt: tokenExpiresAt
    });

    return res.status(200).json({
      success: true,
      resetToken: resetToken,
      message: "Verification code confirmed. You can now create a new password."
    });
  } catch (error) {
    console.error("Error in verifyPasswordResetOtp:", error);
    return res.status(500).json({ success: false, error: "Internal server error during verification." });
  }
});

/**
 * 1.3 RESET PASSWORD WITH VERIFIED TOKEN
 * Validates new password rules, updates Firebase Authentication password via Admin SDK,
 * revokes all sessions, and clears reset token.
 */
exports.resetPasswordWithToken = onRequest(functionOptions, async (req, res) => {
  if (req.method !== "POST") {
    return res.status(405).json({ success: false, error: "Method Not Allowed" });
  }

  const { email, resetToken, newPassword } = req.body || {};
  if (!email || !resetToken || !newPassword) {
    return res.status(400).json({ success: false, error: "Email, reset token, and new password are required." });
  }

  const passwordError = validatePasswordRequirements(newPassword);
  if (passwordError) {
    return res.status(400).json({ success: false, error: passwordError });
  }

  const normalizedEmail = email.trim().toLowerCase();
  const emailDocId = hashEmail(normalizedEmail);
  const docRef = db.collection("password_reset_otps").document(emailDocId);

  try {
    const docSnap = await docRef.get();
    if (!docSnap.exists) {
      return res.status(400).json({ success: false, error: "Invalid password reset request." });
    }

    const data = docSnap.data();
    const now = Date.now();

    // Verify token validity
    if (!data.isVerified || data.passwordResetCompleted) {
      return res.status(400).json({ success: false, error: "This password reset session has expired or already completed." });
    }

    if (!data.resetToken || data.resetToken !== resetToken) {
      return res.status(403).json({ success: false, error: "Invalid or expired reset token." });
    }

    if (now > data.tokenExpiresAt) {
      return res.status(400).json({ success: false, error: "Password reset session has expired. Please restart the process." });
    }

    // Update password in Firebase Authentication securely via Admin SDK
    await admin.auth().updateUser(data.uid, { password: newPassword });

    // Revoke all refresh tokens to terminate any active sessions
    await admin.auth().revokeRefreshTokens(data.uid);

    // Invalidate reset token and mark session completed
    await docRef.update({
      resetToken: null,
      tokenExpiresAt: null,
      passwordResetCompleted: true
    });

    return res.status(200).json({
      success: true,
      message: "Your StudyMate password has been updated successfully."
    });
  } catch (error) {
    console.error("Error in resetPasswordWithToken:", error);
    return res.status(500).json({
      success: false,
      error: error.message || "Failed to update password."
    });
  }
});

// ============================================================================
// 2. CREATE ACCOUNT EMAIL OTP FLOW
// ============================================================================

/**
 * 2.1 REQUEST SIGNUP OTP
 * Validates email, verifies user doesn't already exist, generates 6-digit OTP,
 * hashes with salt & pepper, stores in signup_otps, sends verification email.
 */
exports.requestSignupOtp = onRequest(functionOptions, async (req, res) => {
  if (req.method !== "POST") {
    return res.status(405).json({ success: false, error: "Method Not Allowed" });
  }

  const { email, fullName } = req.body || {};
  if (!email || typeof email !== "string" || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim())) {
    return res.status(400).json({ success: false, error: "Please enter a valid email address." });
  }

  const normalizedEmail = email.trim().toLowerCase();
  const cleanName = (typeof fullName === "string" && fullName.trim().length > 0) ? fullName.trim() : "Student";
  const emailDocId = hashEmail(normalizedEmail);
  const docRef = db.collection("signup_otps").document(emailDocId);

  try {
    // Verify user does not already exist in Firebase Authentication
    try {
      const existingUser = await admin.auth().getUserByEmail(normalizedEmail);
      if (existingUser) {
        return res.status(400).json({
          success: false,
          error: "An account with this email address already exists. Please log in."
        });
      }
    } catch (e) {
      if (e.code !== "auth/user-not-found") {
        throw e;
      }
      // User not found is expected for signup
    }

    // Check resend cooldown (30 seconds)
    const now = Date.now();
    const docSnap = await docRef.get();
    if (docSnap.exists) {
      const existingData = docSnap.data();
      if (existingData.cooldownUntil && now < existingData.cooldownUntil) {
        const remainingSeconds = Math.ceil((existingData.cooldownUntil - now) / 1000);
        return res.status(429).json({
          success: false,
          error: `Please wait ${remainingSeconds} seconds before requesting a new code.`,
          cooldownRemainingSeconds: remainingSeconds
        });
      }
    }

    // Generate random 6-digit OTP (100000 - 999999)
    const rawOtp = crypto.randomInt(100000, 1000000).toString();
    const salt = crypto.randomBytes(16).toString("hex");
    const otpHash = hashOtp(rawOtp, salt);

    const expiresAt = now + OTP_EXPIRY_MS;
    const cooldownUntil = now + RESEND_COOLDOWN_MS;

    // Save record in Firestore (never plaintext OTP)
    await docRef.set({
      email: normalizedEmail,
      fullName: cleanName,
      salt: salt,
      otpHash: otpHash,
      createdAt: now,
      expiresAt: expiresAt,
      cooldownUntil: cooldownUntil,
      attempts: 0,
      maxAttempts: MAX_INCORRECT_ATTEMPTS,
      isUsed: false,
      isVerified: false,
      signupToken: null,
      tokenExpiresAt: null,
      accountCreated: false
    });

    // Send professional signup verification email
    const emailSubject = "StudyMate Account Verification OTP";
    const emailBody = `Hello ${cleanName},

Welcome to StudyMate! To complete your account creation, please verify your email address.

Your verification code is:

${rawOtp}

This code will expire in 5 minutes.

For your security, never share this code with anyone.

Regards,
StudyMate Team

Student Academic Intelligence`;

    const transporter = createTransporter();
    await transporter.sendMail({
      from: `"${SENDER_DISPLAY_NAME}" <${SENDER_EMAIL}>`,
      to: normalizedEmail,
      subject: emailSubject,
      text: emailBody
    });

    return res.status(200).json({
      success: true,
      message: "A verification code has been sent to your email address.",
      cooldownSeconds: 30,
      expiresInSeconds: 300
    });
  } catch (error) {
    console.error("Error in requestSignupOtp:", error);
    return res.status(500).json({
      success: false,
      error: "Unable to send verification code. Please check backend email configuration."
    });
  }
});

/**
 * 2.2 VERIFY SIGNUP OTP
 * Validates 6-digit OTP, checks expiration & max 5 attempts,
 * immediately invalidates OTP, issues single-use signupToken.
 */
exports.verifySignupOtp = onRequest(functionOptions, async (req, res) => {
  if (req.method !== "POST") {
    return res.status(405).json({ success: false, error: "Method Not Allowed" });
  }

  const { email, otp } = req.body || {};
  if (!email || !otp) {
    return res.status(400).json({ success: false, error: "Email and verification code are required." });
  }

  const cleanOtp = String(otp).trim();
  if (cleanOtp.length !== OTP_LENGTH || !/^\d{6}$/.test(cleanOtp)) {
    return res.status(400).json({ success: false, error: "Verification code must be exactly 6 digits." });
  }

  const normalizedEmail = email.trim().toLowerCase();
  const emailDocId = hashEmail(normalizedEmail);
  const docRef = db.collection("signup_otps").document(emailDocId);

  try {
    const docSnap = await docRef.get();
    if (!docSnap.exists) {
      return res.status(400).json({ success: false, error: "No active verification request found. Please request a new code." });
    }

    const data = docSnap.data();
    const now = Date.now();

    // Check if OTP was already used
    if (data.isUsed) {
      return res.status(400).json({ success: false, error: "This verification code has already been used. Please request a new code." });
    }

    // Check expiry (5 minutes)
    if (now > data.expiresAt) {
      return res.status(400).json({ success: false, error: "Verification code has expired. Please request a new code." });
    }

    // Check attempt limit (5 attempts)
    if (data.attempts >= data.maxAttempts) {
      return res.status(429).json({
        success: false,
        error: "Maximum verification attempts exceeded. Please request a new code."
      });
    }

    // Verify OTP using constant-time comparison
    const computedHash = hashOtp(cleanOtp, data.salt);
    const computedBuffer = Buffer.from(computedHash, "hex");
    const storedBuffer = (typeof data.otpHash === "string") ? Buffer.from(data.otpHash, "hex") : Buffer.alloc(0);
    const hashMatches = (computedBuffer.length === storedBuffer.length) && crypto.timingSafeEqual(computedBuffer, storedBuffer);

    if (!hashMatches) {
      const newAttempts = (data.attempts || 0) + 1;
      const remaining = data.maxAttempts - newAttempts;
      await docRef.update({ attempts: newAttempts });

      if (remaining <= 0) {
        return res.status(429).json({
          success: false,
          error: "Maximum verification attempts exceeded. Please request a new code."
        });
      }

      return res.status(400).json({
        success: false,
        error: `Incorrect verification code. ${remaining} attempt${remaining === 1 ? "" : "s"} remaining.`
      });
    }

    // OTP Verified! Immediately invalidate OTP and generate a short-lived signup token
    const signupToken = crypto.randomBytes(32).toString("hex");
    const tokenExpiresAt = now + SIGNUP_TOKEN_EXPIRY_MS;

    await docRef.update({
      isUsed: true, // One-time use: immediately invalidated!
      isVerified: true,
      signupToken: signupToken,
      tokenExpiresAt: tokenExpiresAt
    });

    return res.status(200).json({
      success: true,
      signupToken: signupToken,
      message: "Email verified successfully. You can now complete account creation."
    });
  } catch (error) {
    console.error("Error in verifySignupOtp:", error);
    return res.status(500).json({ success: false, error: "Internal server error during verification." });
  }
});

/**
 * 2.3 CREATE VERIFIED EMAIL ACCOUNT
 * Validates password rules, verifies signupToken, creates user in Firebase Auth with emailVerified=true,
 * mints custom auth token, and invalidates session token.
 */
exports.createVerifiedEmailAccount = onRequest(functionOptions, async (req, res) => {
  if (req.method !== "POST") {
    return res.status(405).json({ success: false, error: "Method Not Allowed" });
  }

  const { email, signupToken, password, fullName } = req.body || {};
  if (!email || !signupToken || !password) {
    return res.status(400).json({ success: false, error: "Email, signup token, and password are required." });
  }

  const passwordError = validatePasswordRequirements(password);
  if (passwordError) {
    return res.status(400).json({ success: false, error: passwordError });
  }

  const normalizedEmail = email.trim().toLowerCase();
  const emailDocId = hashEmail(normalizedEmail);
  const docRef = db.collection("signup_otps").document(emailDocId);

  try {
    const docSnap = await docRef.get();
    if (!docSnap.exists) {
      return res.status(400).json({ success: false, error: "Invalid registration session. Please restart registration." });
    }

    const data = docSnap.data();
    const now = Date.now();

    // Verify token validity
    if (!data.isVerified || data.accountCreated) {
      return res.status(400).json({ success: false, error: "This registration session has expired or already completed." });
    }

    if (!data.signupToken || data.signupToken !== signupToken) {
      return res.status(403).json({ success: false, error: "Invalid or expired signup token." });
    }

    if (now > data.tokenExpiresAt) {
      return res.status(400).json({ success: false, error: "Signup session has expired. Please restart registration." });
    }

    const displayName = (typeof fullName === "string" && fullName.trim().length > 0)
      ? fullName.trim()
      : (data.fullName || "Student");

    // Create user in Firebase Authentication with emailVerified = true
    const userRecord = await admin.auth().createUser({
      email: normalizedEmail,
      password: password,
      displayName: displayName,
      emailVerified: true
    });

    // Create custom auth token for instant, seamless login
    const customToken = await admin.auth().createCustomToken(userRecord.uid);

    // Invalidate signup token and mark session completed
    await docRef.update({
      signupToken: null,
      tokenExpiresAt: null,
      accountCreated: true,
      uid: userRecord.uid
    });

    return res.status(200).json({
      success: true,
      customToken: customToken,
      uid: userRecord.uid,
      message: "StudyMate account created and verified successfully."
    });
  } catch (error) {
    console.error("Error in createVerifiedEmailAccount:", error);
    if (error.code === "auth/email-already-exists") {
      return res.status(400).json({
        success: false,
        error: "An account with this email address already exists. Please log in."
      });
    }
    return res.status(500).json({
      success: false,
      error: error.message || "Failed to create account."
    });
  }
});

// Internal utilities exported for automated unit testing
exports._internal = {
  hashEmail,
  hashOtp,
  validatePasswordRequirements,
  getSecretValue,
  OTP_LENGTH,
  OTP_EXPIRY_MS,
  RESEND_COOLDOWN_MS,
  MAX_INCORRECT_ATTEMPTS,
  RESET_TOKEN_EXPIRY_MS,
  SIGNUP_TOKEN_EXPIRY_MS,
};
