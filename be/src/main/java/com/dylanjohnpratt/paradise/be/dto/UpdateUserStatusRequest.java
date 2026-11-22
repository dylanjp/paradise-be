package com.dylanjohnpratt.paradise.be.dto;

/**
 * Request body for enabling or disabling a user account (admin operation).
 *
 * @param enabled true to enable the account, false to disable it; required
 */
public record UpdateUserStatusRequest(Boolean enabled) {}
