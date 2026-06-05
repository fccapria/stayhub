package com.stayhub.backend.repository;

import com.stayhub.backend.entity.Booking;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Booking b WHERE b.room.id = :roomId AND b.status <> 'CANCELLED' AND b.checkIn < :checkOut AND b.checkOut > :checkIn")
    List<Booking> findOverlappingBookingsForUpdate(
        @Param("roomId") Long roomId,
        @Param("checkIn") LocalDate checkIn,
        @Param("checkOut") LocalDate checkOut
    );

    @Query("SELECT b FROM Booking b WHERE b.room.id = :roomId AND b.status <> 'CANCELLED' AND b.checkIn < :checkOut AND b.checkOut > :checkIn")
    List<Booking> findOverlappingBookings(
        @Param("roomId") Long roomId,
        @Param("checkIn") LocalDate checkIn,
        @Param("checkOut") LocalDate checkOut
    );

    List<Booking> findByUserId(String userId);

    @Query("SELECT b FROM Booking b JOIN FETCH b.room JOIN FETCH b.user WHERE b.id = :id")
    Optional<Booking> findByIdWithRoomAndUser(@Param("id") Long id);

    @Query("SELECT b FROM Booking b JOIN FETCH b.room JOIN FETCH b.user WHERE b.room.owner.id = :ownerId")
    List<Booking> findByRoomOwnerId(@Param("ownerId") String ownerId);
}
