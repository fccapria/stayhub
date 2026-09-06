package com.stayhub.backend.service;

import com.stayhub.backend.dto.RoomDTO;
import com.stayhub.backend.dto.OccupiedRangeDTO;
import com.stayhub.backend.entity.Room;
import com.stayhub.backend.entity.BookingStatus;
import com.stayhub.backend.exception.ResourceNotFoundException;
import com.stayhub.backend.mapper.DtoMapper;
import com.stayhub.backend.repository.RoomRepository;
import com.stayhub.backend.repository.BookingRepository;
import com.stayhub.backend.repository.UserRepository;
import com.stayhub.backend.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoomService {

    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    private final BookingRepository bookingRepository;
    private final FileStorageService fileStorageService;

    @Transactional(readOnly = true)
    public List<RoomDTO> getAllRooms() {
        return roomRepository.findAllByActiveTrue().stream()
                .map(DtoMapper::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public RoomDTO getRoomById(Long id) {
        Room room = roomRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Room not found with ID: " + id));
        RoomDTO dto = DtoMapper.toDto(room);
        dto.setActiveBookingsCount(bookingRepository.countActiveFutureBookings(room.getId(), java.time.LocalDate.now()));
        return dto;
    }

    @Transactional
    public RoomDTO createRoom(RoomDTO roomDTO, String ownerId) {
        if (roomDTO == null) {
            throw new IllegalArgumentException("I dati della camera sono obbligatori.");
        }
        if (roomDTO.getName() == null || roomDTO.getName().trim().length() < 3) {
            throw new IllegalArgumentException("Il nome della camera deve contenere almeno 3 caratteri.");
        }
        if (roomDTO.getDescription() == null || roomDTO.getDescription().trim().length() < 10) {
            throw new IllegalArgumentException("La descrizione della camera deve contenere almeno 10 caratteri.");
        }
        if (roomDTO.getCapacity() == null || roomDTO.getCapacity() < 1) {
            throw new IllegalArgumentException("La capacità deve essere di almeno 1 ospite.");
        }
        if (roomDTO.getPricePerNight() == null || roomDTO.getPricePerNight().compareTo(java.math.BigDecimal.ONE) < 0) {
            throw new IllegalArgumentException("Il prezzo per notte deve essere di almeno 1 €.");
        }

        Room room = DtoMapper.toEntity(roomDTO);
        room.setName(roomDTO.getName().trim());
        room.setDescription(roomDTO.getDescription().trim());
        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + ownerId));
        room.setOwner(owner);
        room.setActive(true);
        Room savedRoom = roomRepository.save(room);
        RoomDTO dto = DtoMapper.toDto(savedRoom);
        dto.setActiveBookingsCount(0L);
        return dto;
    }

    @Transactional(readOnly = true)
    public List<RoomDTO> getRoomsByOwnerId(String ownerId) {
        java.time.LocalDate today = java.time.LocalDate.now();
        return roomRepository.findByOwnerId(ownerId).stream()
                .map(room -> {
                    long activeCount = bookingRepository.countActiveFutureBookings(room.getId(), today);
                    RoomDTO dto = DtoMapper.toDto(room);
                    dto.setActiveBookingsCount(activeCount);
                    return dto;
                })
                .filter(dto -> Boolean.TRUE.equals(dto.getActive()) || (dto.getActiveBookingsCount() != null && dto.getActiveBookingsCount() > 0))
                .collect(Collectors.toList());
    }

    @Transactional
    public void deleteRoom(Long id, String userId, boolean isAdmin) {
        Room room = roomRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Room not found with ID: " + id));

        if (!isAdmin && (room.getOwner() == null || !room.getOwner().getId().equals(userId))) {
            throw new AccessDeniedException("Non sei autorizzato a eliminare questa camera.");
        }

        long bookingsCount = bookingRepository.countByRoomId(id);
        if (bookingsCount == 0) {
            fileStorageService.deleteOldImagesForRoom(id);
            roomRepository.delete(room);
        } else {
            // Soft delete: disattiviamo la stanza dal catalogo pubblico per tutelare i soggiorni in corso,
            // l'integrità dei pagamenti, le ricevute PDF e lo storico contabile.
            // Conserviamo le foto su disco finché esistono prenotazioni associate.
            room.setActive(false);
            roomRepository.save(room);
        }
    }

    @Transactional(readOnly = true)
    public List<OccupiedRangeDTO> getOccupiedRanges(Long roomId) {
        if (!roomRepository.existsById(roomId)) {
            throw new ResourceNotFoundException("Room not found with ID: " + roomId);
        }
        return bookingRepository.findByRoomIdAndStatusNot(roomId, BookingStatus.CANCELLED).stream()
                .map(b -> new OccupiedRangeDTO(b.getCheckIn(), b.getCheckOut()))
                .collect(Collectors.toList());
    }

    @Transactional
    public RoomDTO uploadRoomImage(Long roomId, MultipartFile file, String userId, boolean isAdmin) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Room not found with ID: " + roomId));

        if (!isAdmin && !room.getOwner().getId().equals(userId)) {
            throw new AccessDeniedException("Non sei autorizzato a modificare la foto di questa camera.");
        }

        String imageUrl = fileStorageService.storeRoomImage(roomId, file);
        room.setImageUrl(imageUrl);
        Room updatedRoom = roomRepository.save(room);
        return DtoMapper.toDto(updatedRoom);
    }

    @Transactional(readOnly = true)
    public Resource getRoomImage(Long roomId) {
        if (!roomRepository.existsById(roomId)) {
            throw new ResourceNotFoundException("Room not found with ID: " + roomId);
        }
        return fileStorageService.loadRoomImage(roomId);
    }
}
