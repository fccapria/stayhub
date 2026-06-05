import { Component, OnInit, inject, signal, computed, PLATFORM_ID } from '@angular/core';
import { CommonModule, isPlatformBrowser } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RoomService, RoomDTO } from '../../services/room.service';
import { BookingService, BookingRequestDTO } from '../../services/booking.service';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-room-list',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './room-list.html',
  styleUrl: './room-list.css'
})
export class RoomListComponent implements OnInit {
  private readonly platformId = inject(PLATFORM_ID);
  private readonly roomService = inject(RoomService);
  private readonly bookingService = inject(BookingService);
  protected readonly authService = inject(AuthService);

  // Signals
  rooms = signal<RoomDTO[]>([]);
  selectedRoom = signal<RoomDTO | null>(null);
  checkIn = signal<string>('');
  checkOut = signal<string>('');
  paymentMethod = signal<'CLASSIC_CARD' | 'PAYPAL'>('CLASSIC_CARD');
  
  // Payment Form Fields
  cardHolder = signal<string>('');
  cardNumber = signal<string>('');
  cvv = signal<string>('');
  expirationDate = signal<string>('');
  paypalEmail = signal<string>('');

  // Touched Fields Signals
  cardHolderTouched = signal<boolean>(false);
  cardNumberTouched = signal<boolean>(false);
  cvvTouched = signal<boolean>(false);
  expirationDateTouched = signal<boolean>(false);
  paypalEmailTouched = signal<boolean>(false);
  checkInTouched = signal<boolean>(false);
  checkOutTouched = signal<boolean>(false);

  // Status Alerts
  loading = signal<boolean>(false);
  errorMessage = signal<string | null>(null);
  successMessage = signal<string | null>(null);

  // Derived Values & Validations
  numberOfNights = computed(() => {
    const inStr = this.checkIn();
    const outStr = this.checkOut();
    if (!inStr || !outStr) return 0;
    
    const checkInDate = new Date(inStr);
    const checkOutDate = new Date(outStr);
    if (isNaN(checkInDate.getTime()) || isNaN(checkOutDate.getTime())) return 0;
    
    const diffTime = checkOutDate.getTime() - checkInDate.getTime();
    const diffDays = Math.ceil(diffTime / (1000 * 60 * 60 * 24));
    return diffDays > 0 ? diffDays : 0;
  });

  totalPrice = computed(() => {
    const nights = this.numberOfNights();
    const room = this.selectedRoom();
    if (!room || nights <= 0) return 0;
    return nights * room.pricePerNight;
  });

  // Date validation: check-in must be today or future, check-out must be > check-in
  isDatesValid = computed(() => {
    const inStr = this.checkIn();
    const outStr = this.checkOut();
    if (!inStr || !outStr) return false;
    
    const checkInDate = new Date(inStr);
    const checkOutDate = new Date(outStr);
    if (isNaN(checkInDate.getTime()) || isNaN(checkOutDate.getTime())) return false;
    
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    checkInDate.setHours(0, 0, 0, 0);
    
    return checkInDate.getTime() >= today.getTime() && checkOutDate.getTime() > checkInDate.getTime();
  });

  isCardHolderValid = computed(() => {
    const name = this.cardHolder().trim();
    return name.length >= 3 && /^[a-zA-Z\s]+$/.test(name);
  });

  isCardNumberValid = computed(() => {
    const num = this.cardNumber().replace(/\s+/g, '');
    return /^\d{16}$/.test(num);
  });

  isCvvValid = computed(() => {
    return /^\d{3,4}$/.test(this.cvv().trim());
  });

  isExpirationDateValid = computed(() => {
    const exp = this.expirationDate().trim();
    if (!/^(0[1-9]|1[0-2])\/?([0-9]{2})$/.test(exp)) return false;
    
    const parts = exp.split('/');
    const month = parseInt(parts[0], 10);
    const year = 2000 + parseInt(parts[1], 10);
    
    const now = new Date();
    const currentMonth = now.getMonth() + 1;
    const currentYear = now.getFullYear();
    
    if (year < currentYear) return false;
    if (year === currentYear && month < currentMonth) return false;
    return true;
  });

  isPaypalEmailValid = computed(() => {
    return /^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$/.test(this.paypalEmail().trim());
  });

  isFormValid = computed(() => {
    if (!this.isDatesValid() || this.numberOfNights() <= 0) return false;
    
    if (this.paymentMethod() === 'CLASSIC_CARD') {
      return this.isCardHolderValid() &&
             this.isCardNumberValid() &&
             this.isCvvValid() &&
             this.isExpirationDateValid();
    } else {
      return this.isPaypalEmailValid();
    }
  });

  ngOnInit(): void {
    if (isPlatformBrowser(this.platformId)) {
      this.fetchRooms();
    }
  }

  fetchRooms(): void {
    this.loading.set(true);
    this.roomService.getAllRooms().subscribe({
      next: (data) => {
        this.rooms.set(data);
        this.loading.set(false);
      },
      error: (err) => {
        console.error('Failed to load rooms:', err);
        this.errorMessage.set('Could not fetch room listings. Make sure the backend is running.');
        this.loading.set(false);
      }
    });
  }

  getRoomImageUrl(roomName: string): string {
    const name = roomName.toLowerCase();
    if (name.includes('suite') || name.includes('penthouse') || name.includes('presidential')) {
      return 'assets/suite_room.png';
    }
    return 'assets/deluxe_room.png';
  }

  openBookingModal(room: RoomDTO): void {
    if (!this.authService.isAuthenticated()) {
      this.authService.login();
      return;
    }
    
    this.selectedRoom.set(room);
    // Set default check-in tomorrow, checkout day after
    const tomorrow = new Date();
    tomorrow.setDate(tomorrow.getDate() + 1);
    const dayAfter = new Date();
    dayAfter.setDate(dayAfter.getDate() + 2);
    
    this.checkIn.set(tomorrow.toISOString().split('T')[0]);
    this.checkOut.set(dayAfter.toISOString().split('T')[0]);
    
    // Reset forms & alerts
    this.errorMessage.set(null);
    this.successMessage.set(null);
    this.cardHolder.set('');
    this.cardNumber.set('');
    this.cvv.set('');
    this.expirationDate.set('');
    this.paypalEmail.set('');

    // Reset touched states
    this.cardHolderTouched.set(false);
    this.cardNumberTouched.set(false);
    this.cvvTouched.set(false);
    this.expirationDateTouched.set(false);
    this.paypalEmailTouched.set(false);
    this.checkInTouched.set(false);
    this.checkOutTouched.set(false);
  }

  closeBookingModal(): void {
    this.selectedRoom.set(null);
  }

  submitBooking(): void {
    const room = this.selectedRoom();
    if (!room || !room.id) return;
    
    this.loading.set(true);
    this.errorMessage.set(null);
    
    const request: BookingRequestDTO = {
      roomId: room.id,
      checkIn: this.checkIn(),
      checkOut: this.checkOut(),
      paymentMethod: this.paymentMethod(),
      paymentDetails: this.paymentMethod() === 'CLASSIC_CARD' ? {
        cardHolder: this.cardHolder(),
        cardNumber: this.cardNumber(),
        cvv: this.cvv(),
        expirationDate: this.expirationDate()
      } : {
        paypalEmail: this.paypalEmail(),
        paypalOrderId: 'PAY-' + Math.random().toString(36).substring(2, 9).toUpperCase()
      }
    };

    this.bookingService.createBooking(request).subscribe({
      next: (response) => {
        this.loading.set(false);
        this.successMessage.set('Booking reservation successfully confirmed and paid!');
        setTimeout(() => {
          this.closeBookingModal();
        }, 2500);
      },
      error: (err) => {
        this.loading.set(false);
        console.error('Booking failed:', err);
        const errMsg = err.error?.message || 'Booking failed. Please check your details and try again.';
        this.errorMessage.set(errMsg);
      }
    });
  }
}
