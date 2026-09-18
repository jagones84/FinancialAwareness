// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (c) 2026 jagones84

package com.example.daysurpopt.logic

import android.content.Context
import android.util.Base64
import android.util.Log
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.IntegrityTokenRequest
import java.security.SecureRandom

object PlayIntegrityHelper {
    private const val TAG = "PlayIntegrityHelper"

    // TODO: Replace with your actual Google Cloud Project Number from the Google Play Console
    private const val CLOUD_PROJECT_NUMBER = 1234567890L 

    fun checkIntegrity(context: Context, onSuccess: (String) -> Unit, onError: (Exception) -> Unit) {
        val nonce = generateNonce()
        
        // Create an instance of a manager.
        val integrityManager = IntegrityManagerFactory.create(context)

        // Request the integrity token.
        val integrityTokenResponse = integrityManager.requestIntegrityToken(
            IntegrityTokenRequest.builder()
                .setCloudProjectNumber(CLOUD_PROJECT_NUMBER)
                .setNonce(nonce)
                .build()
        )

        integrityTokenResponse.addOnSuccessListener { response ->
            val integrityToken = response.token()
            Log.d(TAG, "Integrity token received: $integrityToken")
            // In a real app, send this token to your backend for verification.
            onSuccess(integrityToken)
        }

        integrityTokenResponse.addOnFailureListener { e ->
            Log.e(TAG, "Integrity token request failed", e)
            onError(e)
        }
    }

    private fun generateNonce(): String {
        val length = 50
        val nonce = ByteArray(length)
        SecureRandom().nextBytes(nonce)
        return Base64.encodeToString(nonce, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }
}
