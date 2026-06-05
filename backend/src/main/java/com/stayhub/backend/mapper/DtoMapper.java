package com.stayhub.backend.mapper;

import com.stayhub.backend.dto.BookingResponseDTO;
import com.stayhub.backend.dto.RoomDTO;
import com.stayhub.backend.dto.UserDTO;
import com.stayhub.backend.entity.Booking;
import com.stayhub.backend.entity.Payment;
import com.stayhub.backend.entity.Room;
import com.stayhub.backend.entity.User;

public class DtoMapper {

    public static UserDTO toDto(User user) {
        if (user == null) return null;
        return UserDTO.builder()
                .id(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .role(user.getRole())
                .build();
    }

    public static User toEntity(UserDTO dto) {
        if (dto == null) return null;
        return User.builder()
                .id(dto.getId())
                .email(dto.getEmail())
                .firstName(dto.getFirstName())
                .lastName(dto.getLastName())
                .role(dto.getRole())
                .build();
    }

    public static RoomDTO toDto(Room room) {
        if (room == null) return null;
        return RoomDTO.builder()
                .id(room.getId())
                .name(room.getName())
                .description(room.getDescription())
                .capacity(room.getCapacity())
                .pricePerNight(room.getPricePerNight())
                .ownerId(room.getOwner() != null ? room.getOwner().getId() : null)
                .build();
    }

    public static Room toEntity(RoomDTO dto) {
        if (dto == null) return null;
        return Room.builder()
                .id(dto.getId())
                .name(dto.getName())
                .description(dto.getDescription())
                .capacity(dto.getCapacity())
                .pricePerNight(dto.getPricePerNight())
                .owner(dto.getOwnerId() != null ? User.builder().id(dto.getOwnerId()).build() : null)
                .build();
    }

    public static BookingResponseDTO toDto(Booking booking, Payment payment) {
        if (booking == null) return null;
        return BookingResponseDTO.builder()
                .id(booking.getId())
                .userId(booking.getUser() != null ? booking.getUser().getId() : null)
                .roomId(booking.getRoom() != null ? booking.getRoom().getId() : null)
                .checkIn(booking.getCheckIn())
                .checkOut(booking.getCheckOut())
                .totalPrice(booking.getTotalPrice())
                .status(booking.getStatus())
                .paymentStatus(payment != null ? payment.getStatus() : null)
                .transactionReference(payment != null ? payment.getTransactionReference() : null)
                .build();
    }
}
