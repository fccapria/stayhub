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

    @Query("SELECT b FROM Booking b JOIN FETCH b.room r LEFT JOIN FETCH r.owner JOIN FETCH b.user WHERE b.user.id = :userId")
    List<Booking> findByUserId(@Param("userId") String userId);

    @Query("SELECT b FROM Booking b JOIN FETCH b.room r LEFT JOIN FETCH r.owner JOIN FETCH b.user WHERE b.id = :id")
    Optional<Booking> findByIdWithRoomAndUser(@Param("id") Long id);

    @Query("SELECT b FROM Booking b JOIN FETCH b.room r LEFT JOIN FETCH r.owner JOIN FETCH b.user WHERE r.owner.id = :ownerId")
    List<Booking> findByRoomOwnerId(@Param("ownerId") String ownerId);

    List<Booking> findByRoomIdAndStatusNot(Long roomId, com.stayhub.backend.entity.BookingStatus status);

    long countByRoomId(Long roomId);

    @Query("SELECT COUNT(b) FROM Booking b WHERE b.room.id = :roomId AND b.status = 'CONFIRMED' AND b.checkOut >= :today")
    long countActiveFutureBookings(@Param("roomId") Long roomId, @Param("today") LocalDate today);
}
