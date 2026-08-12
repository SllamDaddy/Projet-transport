package com.example.gareter.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.gareter.data.repository.RouteRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LoginViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = RouteRepository(application)

    private val _agent = MutableStateFlow("")
    val agent = _agent.asStateFlow()

    private val _password = MutableStateFlow("")
    val password = _password.asStateFlow()

    sealed interface LoginState {
        object Idle : LoginState
        object Loading : LoginState
        data class Success(val name: String) : LoginState
        data class Error(val message: String) : LoginState
    }

    private val _loginState = MutableStateFlow<LoginState>(LoginState.Idle)
    val loginState = _loginState.asStateFlow()

    fun setAgent(value: String) {
        _agent.value = value
        _loginState.value = LoginState.Idle
    }

    fun setPassword(value: String) {
        _password.value = value
        _loginState.value = LoginState.Idle
    }

    fun login() {
        val agentVal = _agent.value.trim()
        val passwordVal = _password.value.trim()

        if (agentVal.isEmpty() || passwordVal.isEmpty()) {
            _loginState.value = LoginState.Error("Veuillez remplir tous les champs.")
            return
        }

        viewModelScope.launch {
            _loginState.value = LoginState.Loading
            val result = repository.loginDriver(agentVal, passwordVal)
            result.fold(
                onSuccess = { name ->
                    _loginState.value = LoginState.Success(name)
                },
                onFailure = { error ->
                    _loginState.value = LoginState.Error(error.message ?: "Une erreur inconnue est survenue.")
                }
            )
        }
    }

    fun clearState() {
        _loginState.value = LoginState.Idle
    }
}
