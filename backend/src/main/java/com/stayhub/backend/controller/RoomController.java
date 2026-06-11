package com.stayhub.backend.controller;

import com.stayhub.backend.dto.RoomDTO;
import com.stayhub.backend.service.RoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;

    @GetMapping
    public ResponseEntity<List<RoomDTO>> getAllRooms() {
        return ResponseEntity.ok(roomService.getAllRooms());
    }

    @GetMapping("/my")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<RoomDTO>> getMyRooms(@AuthenticationPrincipal Jwt jwt) {
        String ownerId = jwt.getSubject();
        return ResponseEntity.ok(roomService.getRoomsByOwnerId(ownerId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<RoomDTO> getRoomById(@PathVariable Long id) {
        return ResponseEntity.ok(roomService.getRoomById(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RoomDTO> createRoom(@RequestBody RoomDTO roomDTO, @AuthenticationPrincipal Jwt jwt) {
        String ownerId = jwt.getSubject();
        RoomDTO created = roomService.createRoom(roomDTO, ownerId);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteRoom(@PathVariable Long id) {
        roomService.deleteRoom(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/occupied")
    public ResponseEntity<List<com.stayhub.backend.dto.OccupiedRangeDTO>> getOccupiedRanges(@PathVariable Long id) {
        return ResponseEntity.ok(roomService.getOccupiedRanges(id));
    }
}
