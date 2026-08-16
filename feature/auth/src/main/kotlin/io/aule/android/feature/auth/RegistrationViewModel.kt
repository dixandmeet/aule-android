package io.aule.android.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.aule.android.core.common.log.AuleLogger
import io.aule.android.core.common.log.LogDomain
import io.aule.android.core.model.AuthException
import io.aule.android.core.model.AuthFailureKind
import io.aule.android.core.model.ProRegistrationDraft
import io.aule.android.core.model.ProfessionalProfile
import io.aule.android.core.model.ProfessionalTransportMode
import io.aule.android.core.model.repository.AuthRepository
import io.aule.android.core.model.repository.RegistrationDraftStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RegistrationUiState(
    val isHydrated: Boolean = false,
    val step: RegistrationStep = RegistrationStep.WELCOME,
    val draft: ProRegistrationDraft = ProRegistrationDraft(),
    val password: String = "",
    val confirmPassword: String = "",
    val showPassword: Boolean = false,
    val networkQuery: String = "",
    val isSubmitting: Boolean = false,
    val isResending: Boolean = false,
    val failure: AuthFailureKind? = null,
    val missingProfessionalData: Boolean = false,
    val notice: RegistrationNotice? = null,
) {
    val emailValid: Boolean
        get() = EMAIL_PATTERN.matches(draft.email.trim())

    val passwordMismatch: Boolean
        get() = confirmPassword.isNotEmpty() && confirmPassword != password

    val accountComplete: Boolean
        get() = emailValid &&
            password.length >= MIN_PASSWORD_LENGTH &&
            password == confirmPassword &&
            draft.termsAccepted

    val canContinue: Boolean
        get() = when (step) {
            RegistrationStep.PROFILE -> draft.profiles.isNotEmpty()
            RegistrationStep.NETWORK -> draft.networkComplete
            RegistrationStep.IDENTITY -> draft.identityComplete
            RegistrationStep.TRANSPORT_MODE -> draft.transportMode != null
            RegistrationStep.ACCOUNT -> accountComplete
            RegistrationStep.WELCOME, RegistrationStep.CONFIRMATION -> true
        }

    val flow: List<RegistrationStep>
        get() = flowFor(
            if (draft.profiles.isEmpty()) {
                draft.copy(profiles = setOf(ProfessionalProfile.CONDUCTEUR))
            } else {
                draft
            },
        )

    val actionSteps: List<RegistrationStep>
        get() = flow.filter {
            it != RegistrationStep.WELCOME && it != RegistrationStep.CONFIRMATION
        }

    val actionIndex: Int
        get() = actionSteps.indexOf(step).coerceAtLeast(0)

    val showsNaolib: Boolean
        get() {
            val query = networkQuery.trim().lowercase()
            if (query.isEmpty()) return true
            return "naolib".contains(query) ||
                "nantes métropole".contains(query) ||
                "nantes metropole".contains(query)
        }
}

class RegistrationViewModel(
    private val auth: AuthRepository,
    private val drafts: RegistrationDraftStore,
    private val logger: AuleLogger,
) : ViewModel() {

    private val _state = MutableStateFlow(RegistrationUiState())
    val state: StateFlow<RegistrationUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { hydrate() }
    }

    fun continueForward() {
        val current = _state.value
        if (!current.canContinue || current.isSubmitting) return
        if (current.step == RegistrationStep.ACCOUNT) {
            submit()
            return
        }
        val flow = current.flow
        val index = flow.indexOf(current.step)
        if (index >= 0 && index < flow.lastIndex) {
            setStep(flow[index + 1])
        }
    }

    fun back(onLeave: () -> Unit) {
        val current = _state.value
        val flow = current.flow
        val index = flow.indexOf(current.step)
        if (index > 0) {
            setStep(flow[index - 1])
        } else {
            onLeave()
        }
    }

    fun finish(onLeave: () -> Unit) {
        viewModelScope.launch {
            drafts.clear()
            _state.value = RegistrationUiState(isHydrated = true)
            onLeave()
        }
    }

    fun toggleProfile(profile: ProfessionalProfile) {
        updateDraft { it.toggleProfile(profile) }
    }

    fun selectNaolib() {
        updateDraft { it.copy(networkKey = ProRegistrationDraft.NAOLIB_NETWORK_KEY) }
    }

    fun setNetworkQuery(query: String) {
        _state.value = _state.value.copy(networkQuery = query)
    }

    fun setFullName(value: String) {
        updateDraft { it.copy(fullName = value) }
    }

    fun setEmployeeId(value: String) {
        updateDraft { it.copy(employeeId = value) }
    }

    fun setTransportMode(mode: ProfessionalTransportMode) {
        updateDraft { it.copy(transportMode = mode) }
    }

    fun setEmail(value: String) {
        updateDraft { it.copy(email = value) }
    }

    fun setPassword(value: String) {
        _state.value = _state.value.copy(password = value, failure = null)
    }

    fun setConfirmPassword(value: String) {
        _state.value = _state.value.copy(confirmPassword = value, failure = null)
    }

    fun toggleShowPassword() {
        _state.value = _state.value.copy(showPassword = !_state.value.showPassword)
    }

    fun toggleTerms() {
        updateDraft { it.copy(termsAccepted = !it.termsAccepted) }
    }

    fun clearFailure() {
        val current = _state.value
        if (current.failure == null && !current.missingProfessionalData) return
        _state.value = current.copy(failure = null, missingProfessionalData = false)
    }

    fun resendConfirmation() {
        val current = _state.value
        if (current.isResending || current.draft.email.trim().isEmpty()) return
        _state.value = current.copy(isResending = true, notice = null)
        viewModelScope.launch {
            try {
                auth.resendSignupConfirmation(current.draft.email)
                _state.value = _state.value.copy(
                    isResending = false,
                    notice = RegistrationNotice.CONFIRMATION_SENT,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: AuthException) {
                _state.value = _state.value.copy(
                    isResending = false,
                    notice = if (failure.kind == AuthFailureKind.RATE_LIMITED) {
                        RegistrationNotice.RATE_LIMITED
                    } else {
                        RegistrationNotice.RESEND_FAILED
                    },
                )
            } catch (failure: Throwable) {
                logger.warn(LogDomain.AUTH, "Renvoi de confirmation impossible.", failure)
                _state.value = _state.value.copy(
                    isResending = false,
                    notice = RegistrationNotice.RESEND_FAILED,
                )
            }
        }
    }

    private fun submit() {
        val current = _state.value
        if (!current.draft.professionalDataComplete || !current.accountComplete) {
            _state.value = current.copy(missingProfessionalData = true, failure = null)
            return
        }
        _state.value = current.copy(
            isSubmitting = true,
            failure = null,
            missingProfessionalData = false,
        )
        viewModelScope.launch {
            try {
                auth.signUpProfessional(current.draft, current.password)
                setStep(
                    RegistrationStep.CONFIRMATION,
                    password = "",
                    confirmPassword = "",
                    submitting = false,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: AuthException) {
                logger.info(LogDomain.AUTH, "Inscription refusée (${failure.kind}).")
                _state.value = _state.value.copy(
                    isSubmitting = false,
                    failure = failure.kind,
                )
            } catch (failure: Throwable) {
                logger.warn(LogDomain.AUTH, "Inscription en échec.", failure)
                _state.value = _state.value.copy(
                    isSubmitting = false,
                    failure = AuthFailureKind.NETWORK,
                )
            }
        }
    }

    private fun setStep(
        step: RegistrationStep,
        password: String = _state.value.password,
        confirmPassword: String = _state.value.confirmPassword,
        submitting: Boolean = _state.value.isSubmitting,
    ) {
        _state.value = _state.value.copy(
            step = step,
            password = password,
            confirmPassword = confirmPassword,
            isSubmitting = submitting,
            failure = null,
            missingProfessionalData = false,
            notice = null,
            networkQuery = "",
        )
        persist()
    }

    private fun updateDraft(edit: (ProRegistrationDraft) -> ProRegistrationDraft) {
        _state.value = _state.value.copy(
            draft = edit(_state.value.draft),
            failure = null,
            missingProfessionalData = false,
        )
        persist()
    }

    private fun persist() {
        val snapshot = _state.value
        viewModelScope.launch {
            drafts.write(snapshot.draft.encode(), snapshot.step.storageName)
        }
    }

    private suspend fun hydrate() {
        var draft = ProRegistrationDraft()
        val encoded = drafts.readDraft()
        if (encoded != null) {
            try {
                draft = ProRegistrationDraft.decode(encoded)
            } catch (_: Throwable) {
                drafts.clear()
            }
        }
        var restored = RegistrationStep.fromStorage(drafts.readStep()) ?: RegistrationStep.WELCOME
        val valid = flowFor(draft)
        if (restored !in valid) {
            restored = if (draft.profiles.isEmpty()) {
                RegistrationStep.WELCOME
            } else {
                RegistrationStep.PROFILE
            }
        }
        _state.value = RegistrationUiState(
            isHydrated = true,
            step = restored,
            draft = draft,
        )
    }
}

internal fun flowFor(draft: ProRegistrationDraft): List<RegistrationStep> = buildList {
    add(RegistrationStep.WELCOME)
    add(RegistrationStep.PROFILE)
    add(RegistrationStep.NETWORK)
    add(RegistrationStep.IDENTITY)
    if (draft.asksTransportMode) add(RegistrationStep.TRANSPORT_MODE)
    add(RegistrationStep.ACCOUNT)
    add(RegistrationStep.CONFIRMATION)
}

internal fun passwordScore(password: String): Int {
    var score = 0
    if (password.length >= 8) score++
    if (password.any { it.isUpperCase() } && password.any { it.isDigit() }) score++
    if (password.length >= 12 && password.any { !it.isLetterOrDigit() }) score++
    return score
}

private val EMAIL_PATTERN = Regex("^.+@.+\\..+$")
private const val MIN_PASSWORD_LENGTH = 8
