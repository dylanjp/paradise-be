package com.dylanjohnpratt.paradise.be.repository;

import com.dylanjohnpratt.paradise.be.model.ItemMetadata;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

/**
 * Repository for ItemMetadata persistence operations.
 */
@Repository
public interface ItemMetadataRepository extends JpaRepository<ItemMetadata, String> {

    /**
     * Finds all metadata records for a given drive key.
     *
     * @param driveKey the drive key (e.g., "myDrive")
     * @return list of metadata records for the drive
     */
    List<ItemMetadata> findByDriveKey(String driveKey);

    /**
     * Deletes all metadata records whose item IDs are in the given collection.
     * Used for cascading cleanup when items are deleted.
     *
     * @param itemIds the collection of item IDs to delete
     */
    @Modifying
    @Transactional
    void deleteByItemIdIn(Collection<String> itemIds);
}
