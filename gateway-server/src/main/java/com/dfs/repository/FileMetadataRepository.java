package com.dfs.repository;

import com.dfs.model.FileMetadata;
import com.dfs.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface FileMetadataRepository extends JpaRepository<FileMetadata, Long> {

    List<FileMetadata> findByOwnerAndDeletedAtIsNull(User owner);

    List<FileMetadata> findByOwnerAndDeletedAtIsNotNull(User owner);

    Optional<FileMetadata> findByIdAndOwnerAndDeletedAtIsNull(Long id, User owner);

    Optional<FileMetadata> findByIdAndOwnerAndDeletedAtIsNotNull(Long id, User owner);

    Optional<FileMetadata> findByStoredName(String storedName);

    @Modifying
    @Query("DELETE FROM FileMetadata f WHERE f.deletedAt IS NOT NULL AND f.deletedAt < :cutoff")
    int deleteExpiredSoftDeletes(@Param("cutoff") LocalDateTime cutoff);
}
