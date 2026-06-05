package com.stayhub.backend.service;

import com.stayhub.backend.dto.RoomDTO;
import com.stayhub.backend.entity.Room;
import com.stayhub.backend.exception.ResourceNotFoundException;
import com.stayhub.backend.mapper.DtoMapper;
import com.stayhub.backend.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stayhub.backend.repository.UserRepository;
import com.stayhub.backend.entity.User;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoomService {

    private final RoomRepository roomRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<RoomDTO> getAllRooms() {
        return roomRepository.findAll().stream()
                .map(DtoMapper::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public RoomDTO getRoomById(Long id) {
        Room room = roomRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Room not found with ID: " + id));
        return DtoMapper.toDto(room);
    }

    @Transactional
    public RoomDTO createRoom(RoomDTO roomDTO) {
        Room room = DtoMapper.toEntity(roomDTO);
        if (roomDTO.getOwnerId() != null) {
            User owner = userRepository.findById(roomDTO.getOwnerId())
                    .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + roomDTO.getOwnerId()));
            room.setOwner(owner);
        }
        Room savedRoom = roomRepository.save(room);
        return DtoMapper.toDto(savedRoom);
    }

    @Transactional
    public RoomDTO createRoom(RoomDTO roomDTO, String ownerId) {
        Room room = DtoMapper.toEntity(roomDTO);
        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + ownerId));
        room.setOwner(owner);
        Room savedRoom = roomRepository.save(room);
        return DtoMapper.toDto(savedRoom);
    }

    @Transactional(readOnly = true)
    public List<RoomDTO> getRoomsByOwnerId(String ownerId) {
        return roomRepository.findByOwnerId(ownerId).stream()
                .map(DtoMapper::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public void deleteRoom(Long id) {
        if (!roomRepository.existsById(id)) {
            throw new ResourceNotFoundException("Room not found with ID: " + id);
        }
        roomRepository.deleteById(id);
    }
}
