package com.dylanjohnpratt.paradise.be.dto;

/**
 * Request body for updating a drive item's name or color.
 * Both fields are optional.
 */
public record UpdateItemRequest(
    String name,
    String color
) {}
