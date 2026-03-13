package com.example.suraksha.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.suraksha.data.EmergencyContact
import com.example.suraksha.data.SurakshaRepository
import com.example.suraksha.SurakshaApplication
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ContactsViewModel(application: Application) : AndroidViewModel(application) {
    
    private val repository = SurakshaRepository(
        SurakshaApplication.database.emergencyContactDao(),
        SurakshaApplication.database.appSettingsDao(),
        SurakshaApplication.database.safetyRecordDao()
    )
    
    // UI State
    private val _uiState = MutableStateFlow(ContactsUiState())
    val uiState: StateFlow<ContactsUiState> = _uiState.asStateFlow()
    
    // Emergency contacts
    val emergencyContacts = repository.getAllActiveContacts()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    
    init {
        viewModelScope.launch {
            emergencyContacts.collect { contacts ->
                _uiState.value = _uiState.value.copy(
                    contactCount = contacts.size,
                    canAddMore = contacts.size < 3
                )
            }
        }
    }
    
    fun addContact(name: String, phoneNumber: String) {
        if (name.isBlank() || phoneNumber.isBlank()) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "Name and phone number are required"
            )
            return
        }
        
        if (!isValidPhoneNumber(phoneNumber)) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "Please enter a valid phone number"
            )
            return
        }
        
        viewModelScope.launch {
            try {
                repository.addContact(name.trim(), phoneNumber.trim())
                _uiState.value = _uiState.value.copy(
                    errorMessage = null,
                    successMessage = "Contact added successfully"
                )
                
                // Clear success message after 3 seconds
                kotlinx.coroutines.delay(3000)
                _uiState.value = _uiState.value.copy(successMessage = null)
                
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to add contact: ${e.message}"
                )
            }
        }
    }
    
    fun updateContact(contact: EmergencyContact) {
        viewModelScope.launch {
            try {
                repository.updateContact(contact)
                _uiState.value = _uiState.value.copy(
                    errorMessage = null,
                    successMessage = "Contact updated successfully"
                )
                
                // Clear success message after 3 seconds
                kotlinx.coroutines.delay(3000)
                _uiState.value = _uiState.value.copy(successMessage = null)
                
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to update contact: ${e.message}"
                )
            }
        }
    }
    
    fun deleteContact(contact: EmergencyContact) {
        viewModelScope.launch {
            try {
                repository.deleteContact(contact)
                _uiState.value = _uiState.value.copy(
                    errorMessage = null,
                    successMessage = "Contact deleted successfully"
                )
                
                // Clear success message after 3 seconds
                kotlinx.coroutines.delay(3000)
                _uiState.value = _uiState.value.copy(successMessage = null)
                
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to delete contact: ${e.message}"
                )
            }
        }
    }


    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
    
    fun clearSuccess() {
        _uiState.value = _uiState.value.copy(successMessage = null)
    }
    
    private fun isValidPhoneNumber(phoneNumber: String): Boolean {
        // Basic phone number validation - can be enhanced
        val cleanNumber = phoneNumber.replace(Regex("[^0-9+]"), "")
        return cleanNumber.length >= 10 && cleanNumber.length <= 15
    }
}

data class ContactsUiState(
    val contactCount: Int = 0,
    val canAddMore: Boolean = true,
    val errorMessage: String? = null,
    val successMessage: String? = null
)
