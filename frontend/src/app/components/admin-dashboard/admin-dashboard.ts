import { Component, OnInit, inject, signal, computed, PLATFORM_ID } from '@angular/core';
import { CommonModule, isPlatformBrowser } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RoomService, RoomDTO } from '../../services/room.service';
import { BookingService, BookingResponseDTO } from '../../services/booking.service';

@Component({
  selector: 'app-admin-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './admin-dashboard.html',
  styleUrl: './admin-dashboard.css'
})
export class AdminDashboardComponent implements OnInit {
  private readonly platformId = inject(PLATFORM_ID);
  private readonly roomService = inject(RoomService);
  private readonly bookingService = inject(BookingService);

  // Lists
  rooms = signal<RoomDTO[]>([]);
  bookings = signal<BookingResponseDTO[]>([]);
  
  // Status Flags
  loading = signal<boolean>(false);
  actionLoading = signal<boolean>(false);
  errorMessage = signal<string | null>(null);
  successMessage = signal<string | null>(null);

  // Add Room Form Fields
  newRoomName = signal<string>('');
  newRoomDescription = signal<string>('');
  newRoomCapacity = signal<number>(2);
  newRoomPrice = signal<number>(100);

  // Stats
  totalRooms = computed(() => this.rooms().length);
  totalBookingsCount = computed(() => this.bookings().length);
  
  confirmedBookingsCount = computed(() => 
    this.bookings().filter(b => b.status === 'CONFIRMED').length
  );
  
  totalRevenues = computed(() => {
    return this.bookings()
      .filter(b => b.status !== 'CANCELLED')
      .reduce((sum, b) => sum + b.totalPrice, 0);
  });

  ngOnInit(): void {
    if (isPlatformBrowser(this.platformId)) {
      this.loadAdminData();
    }
  }

  loadAdminData(): void {
    this.loading.set(true);
    this.errorMessage.set(null);

    // Load owner rooms
    this.roomService.getMyRooms().subscribe({
      next: (roomsData) => {
        this.rooms.set(roomsData);
        // Load owner bookings
        this.bookingService.getOwnerBookings().subscribe({
          next: (bookingsData) => {
            // Sort bookings by checkIn date descending
            const sorted = bookingsData.sort((a, b) => new Date(b.checkIn).getTime() - new Date(a.checkIn).getTime());
            this.bookings.set(sorted);
            this.loading.set(false);
          },
          error: (err) => {
            console.error('Failed to load owner bookings:', err);
            this.errorMessage.set('Could not fetch your reservation records.');
            this.loading.set(false);
          }
        });
      },
      error: (err) => {
        console.error('Failed to load owner rooms:', err);
        this.errorMessage.set('Could not fetch your B&B rooms.');
        this.loading.set(false);
      }
    });
  }

  getRoomName(roomId: number): string {
    const room = this.rooms().find((r) => r.id === roomId);
    return room ? room.name : `Room #${roomId}`;
  }

  submitAddRoom(): void {
    const name = this.newRoomName().trim();
    const description = this.newRoomDescription().trim();
    const capacity = this.newRoomCapacity();
    const price = this.newRoomPrice();

    if (!name || !description || capacity <= 0 || price <= 0) {
      this.errorMessage.set('Please fill out all fields with valid positive values.');
      return;
    }

    this.actionLoading.set(true);
    this.errorMessage.set(null);

    const room: RoomDTO = {
      name,
      description,
      capacity,
      pricePerNight: price
    };

    this.roomService.createRoom(room).subscribe({
      next: (createdRoom) => {
        this.actionLoading.set(false);
        this.successMessage.set(`Room "${createdRoom.name}" was successfully registered.`);
        this.rooms.update((prev) => [...prev, createdRoom]);
        
        // Reset form
        this.newRoomName.set('');
        this.newRoomDescription.set('');
        this.newRoomCapacity.set(2);
        this.newRoomPrice.set(100);

        setTimeout(() => {
          this.successMessage.set(null);
        }, 3000);
      },
      error: (err) => {
        this.actionLoading.set(false);
        console.error('Failed to create room:', err);
        const errMsg = err.error?.message || 'Failed to create room. Please verify details.';
        this.errorMessage.set(errMsg);
      }
    });
  }

  deleteRoom(roomId: number | undefined): void {
    if (roomId === undefined) return;
    
    if (!confirm('Are you sure you want to delete this room? This might cause foreign key constraints errors if bookings already exist.')) {
      return;
    }

    this.actionLoading.set(true);
    this.errorMessage.set(null);

    this.roomService.deleteRoom(roomId).subscribe({
      next: () => {
        this.actionLoading.set(false);
        this.successMessage.set('Room successfully removed.');
        this.rooms.update((prev) => prev.filter((r) => r.id !== roomId));
        
        setTimeout(() => {
          this.successMessage.set(null);
        }, 3000);
      },
      error: (err) => {
        this.actionLoading.set(false);
        console.error('Failed to delete room:', err);
        const errMsg = err.error?.message || 'Could not delete room. It might be linked to active bookings.';
        this.errorMessage.set(errMsg);
        
        setTimeout(() => {
          this.errorMessage.set(null);
        }, 5000);
      }
    });
  }
}
