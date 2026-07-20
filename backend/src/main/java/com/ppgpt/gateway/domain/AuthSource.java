package com.ppgpt.gateway.domain;

/**
 * Authentication source for a user account.
 * LOCAL — credentials stored in local DB (BCrypt).
 * AZURE_AD — authenticated via Azure Active Directory (mock LDAP in this build).
 */
public enum AuthSource {
    LOCAL,
    AZURE_AD
}
