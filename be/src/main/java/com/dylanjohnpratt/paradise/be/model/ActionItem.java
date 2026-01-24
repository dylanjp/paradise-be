package com.dylanjohnpratt.paradise.be.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.util.Objects;

/**
 * Embeddable value object representing an action item that can be attached to a notification.
 * When a user actions a notification with an ActionItem, a TodoTask is created.
 */
@Embeddable
public class ActionItem {

    @Column(name = "action_description")
    private String description;

    @Column(name = "action_category")
    private String category;

    public ActionItem() {
    }

    public ActionItem(String description, String category) {
        this.description = description;
        this.category = category;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ActionItem that = (ActionItem) o;
        return Objects.equals(description, that.description) &&
                Objects.equals(category, that.category);
    }

    @Override
    public int hashCode() {
        return Objects.hash(description, category);
    }

    @Override
    public String toString() {
        return "ActionItem{" +
                "description='" + description + '\'' +
                ", category='" + category + '\'' +
                '}';
    }
}
