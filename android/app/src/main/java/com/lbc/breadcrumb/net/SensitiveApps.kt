package com.lbc.breadcrumb.net

/**
 * Packages whose saves must never leave the phone (local-first L4):
 * banking, UPI, and password-manager apps.
 *
 * Centralized list so all checks reference the single source of truth.
 */
object SensitiveApps {
    val PACKAGES: Set<String> = setOf(
        // UPI & Payments
        "com.google.android.apps.nbu.paisa.user", // Google Pay
        "com.phonepe.app",                       // PhonePe
        "net.one97.paytm",                        // Paytm
        "in.org.npci.upiapp",                     // BHIM
        "com.dreamplug.androidapp",               // CRED
        "com.mobikwik_new",                       // MobiKwik
        "com.amazon.mShop.android.shopping",      // Amazon Pay UPI
        "money.super",                            // super.money
        "com.navi.finance",                       // Navi UPI

        // Big Indian Banks
        "com.sbi.lotusintouch",                   // SBI YONO
        "com.sbi.SBIFreedomPlus",                 // SBI Anywhere
        "com.snapwork.hdfc",                      // HDFC Bank MobileBanking
        "com.csam.icici.bank.imobile",            // ICICI Bank iMobile
        "com.axis.mobile",                        // Axis Mobile
        "com.kotak.omnie",                        // Kotak 811
        "com.msf.kbank.mobile",                   // Kotak Mobile Banking
        "com.pnb.pnbone",                         // PNB ONE
        "com.bankofbaroda.mconnect",              // bob World
        "com.canarabank.mobility",                // Canara ai1
        "com.infrasofttech.uboi",                 // Union Bank Vyom
        "com.indusind.mobile",                    // IndusInd Bank
        "com.fedmobile",                          // FedMobile
        "com.idfcfirstbank.optimus",              // IDFC FIRST Bank

        // Password Managers & 2FA
        "com.onepassword.android",                // 1Password
        "com.x8bit.bitwarden",                    // Bitwarden
        "com.google.android.gms",                 // Google Password Manager / Autofill
        "com.google.android.gms.passwords",       // Google Password Manager shortcut
        "com.google.android.apps.authenticator2", // Google Authenticator
        "com.dashlane",                           // Dashlane
        "com.lastpass.lpandroid",                 // LastPass
        "proton.android.pass",                    // Proton Pass
        "com.nordpass.android",                   // NordPass
        "io.enpass.app",                          // Enpass
        "com.kunzisoft.keepass.free",             // KeePassDX Free
        "com.kunzisoft.keepass.pro",              // KeePassDX Pro
        "keepass2android.keepass2android",        // KeePass2Android
        "com.azure.authenticator",                // Microsoft Authenticator
    )

    fun isSensitive(sourceApp: String?): Boolean {
        if (sourceApp.isNullOrBlank()) return false
        return sourceApp in PACKAGES
    }
}
