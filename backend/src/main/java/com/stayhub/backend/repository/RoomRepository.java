package com.stayhub.backend.repository;

import com.stayhub.backend.entity.Room;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RoomRepository extends JpaRepository<Room, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Room r WHERE r.id = :id")
    Optional<Room> findByIdForUpdate(@Param("id") Long id);

    java.util.List<Room> findByOwnerId(String ownerId);

    java.util.List<Room> findAllByActiveTrue();

    java.util.List<Room> findByOwnerIdAndActiveTrue(String ownerId);

    Optional<Room> findByIdAndActiveTrue(Long id);
}
