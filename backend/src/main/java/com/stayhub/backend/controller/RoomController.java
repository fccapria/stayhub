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
    @PreAuthorize("hasAnyRole('HOST', 'ADMIN')")
    public ResponseEntity<List<RoomDTO>> getMyRooms(@AuthenticationPrincipal Jwt jwt) {
        String ownerId = jwt.getSubject();
        return ResponseEntity.ok(roomService.getRoomsByOwnerId(ownerId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<RoomDTO> getRoomById(@PathVariable Long id) {
        return ResponseEntity.ok(roomService.getRoomById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('HOST', 'ADMIN')")
    public ResponseEntity<RoomDTO> createRoom(@RequestBody RoomDTO roomDTO, @AuthenticationPrincipal Jwt jwt) {
        String ownerId = jwt.getSubject();
        RoomDTO created = roomService.createRoom(roomDTO, ownerId);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('HOST', 'ADMIN')")
    public ResponseEntity<Void> deleteRoom(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt,
            org.springframework.security.core.Authentication authentication) {
        boolean isAdmin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        roomService.deleteRoom(id, jwt.getSubject(), isAdmin);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/occupied")
    public ResponseEntity<List<com.stayhub.backend.dto.OccupiedRangeDTO>> getOccupiedRanges(@PathVariable Long id) {
        return ResponseEntity.ok(roomService.getOccupiedRanges(id));
    }

    @PostMapping(value = "/{id}/image", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('HOST', 'ADMIN')")
    public ResponseEntity<RoomDTO> uploadRoomImage(
            @PathVariable Long id,
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file,
            @AuthenticationPrincipal Jwt jwt,
            org.springframework.security.core.Authentication authentication) {
        boolean isAdmin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        RoomDTO updated = roomService.uploadRoomImage(id, file, jwt.getSubject(), isAdmin);
        return ResponseEntity.ok(updated);
    }

    @GetMapping("/{id}/image")
    public ResponseEntity<org.springframework.core.io.Resource> getRoomImage(@PathVariable Long id) {
        org.springframework.core.io.Resource resource = roomService.getRoomImage(id);
        if (resource == null || !resource.exists()) {
            return ResponseEntity.notFound().build();
        }

        String filename = resource.getFilename();
        org.springframework.http.MediaType mediaType = org.springframework.http.MediaType.IMAGE_JPEG;
        if (filename != null) {
            if (filename.toLowerCase().endsWith(".png")) {
                mediaType = org.springframework.http.MediaType.IMAGE_PNG;
            } else if (filename.toLowerCase().endsWith(".webp")) {
                mediaType = org.springframework.http.MediaType.parseMediaType("image/webp");
            }
        }

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(org.springframework.http.HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                .body(resource);
    }
}
