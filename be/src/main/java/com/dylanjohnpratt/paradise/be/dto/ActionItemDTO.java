package com.dylanjohnpratt.paradise.be.dto;

import com.dylanjohnpratt.paradise.be.model.ActionItem;

/**
 * DTO for action item data in notifications.
 * Represents an optional component that can generate a TODO task when actioned.
 */
public record ActionItemDTO(
    String description,
    String category
) {
    /**
     * Creates an ActionItemDTO from an ActionItem entity.
     * @param actionItem the entity to convert
     * @return the DTO, or null if the entity is null
     */
    public static ActionItemDTO fromEntity(ActionItem actionItem) {
        if (actionItem == null) {
            return null;
        }
        return new ActionItemDTO(actionItem.getDescription(), actionItem.getCategory());
    }

    /**
     * Converts this DTO to an ActionItem entity.
     * @return the entity
     */
    public ActionItem toEntity() {
        return new ActionItem(description, category);
    }
}
