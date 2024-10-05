package com.example.adminapp.model

data class PasswordPolicy(
    val min_length: Int? = null,
    val require_special_chars: Boolean? = null,
    val expiration_days: Int? = null,
)