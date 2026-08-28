package io.aule.android.core.security

import android.content.Context
import android.content.pm.PackageManager
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG

/**
 * Ce que l'appareil sait faire, derrière une interface.
 *
 * L'interface existe pour que l'appel qui décide de **proposer** la biométrie
 * reste vérifiable : `BiometricManager` est final et ne se feint pas, alors que
 * cette question-là — proposer ou non — est précisément celle qui mérite un
 * test.
 */
interface BiometricSupport {

    /** L'état du capteur, en `BIOMETRIC_STRONG` — le seul niveau accepté ici. */
    fun availability(): BiometricAvailability

    /** Le mot à employer à l'écran : « empreinte », ou « biométrie ». */
    fun detectedType(): BiometricType
}

/**
 * L'implémentation qui interroge réellement Android.
 *
 * ## Pourquoi `BIOMETRIC_STRONG` seul
 *
 * Ni `BIOMETRIC_WEAK`, ni surtout `DEVICE_CREDENTIAL`. Le premier accepte des
 * capteurs que le système juge trop faciles à tromper ; le second remplacerait
 * l'empreinte par le code de verrouillage du téléphone, qui est justement ce
 * que voit par-dessus l'épaule quiconque regarde son voisin déverrouiller son
 * écran. Et `BIOMETRIC_STRONG` est le seul niveau qui puisse porter une clé
 * Keystore : sans lui, plus de `CryptoObject`, donc plus de preuve.
 *
 * Le repli, ici, n'est pas dans le dialogue système — c'est le formulaire de
 * connexion, derrière un bouton « Se connecter autrement ».
 */
class AndroidBiometricSupport(
    context: Context,
) : BiometricSupport {

    private val appContext = context.applicationContext

    override fun availability(): BiometricAvailability =
        when (BiometricManager.from(appContext).canAuthenticate(BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricAvailability.READY
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricAvailability.NO_HARDWARE
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE ->
                BiometricAvailability.HARDWARE_UNAVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricAvailability.NONE_ENROLLED
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED ->
                BiometricAvailability.SECURITY_UPDATE_REQUIRED
            else -> BiometricAvailability.UNKNOWN
        }

    /**
     * Les trois `FEATURE_*` sont des constantes de chaîne, donc citables sous
     * l'API 29 où deux d'entre elles n'existent pas encore : `hasSystemFeature`
     * rend simplement `false` pour un nom qu'il ne connaît pas. Aucun garde de
     * version à écrire, et le plancher du projet est l'API 26.
     */
    override fun detectedType(): BiometricType {
        val packages = appContext.packageManager
        return BiometricTypeResolver.resolve(
            hasFingerprintFeature = packages.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT),
            hasFaceFeature = packages.hasSystemFeature(PackageManager.FEATURE_FACE),
            hasIrisFeature = packages.hasSystemFeature(PackageManager.FEATURE_IRIS),
        )
    }
}
