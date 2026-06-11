import { Component, OnInit, inject, signal, computed, PLATFORM_ID } from '@angular/core';
import { CommonModule, isPlatformBrowser } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RoomService, RoomDTO } from '../../services/room.service';
import { BookingService, BookingRequestDTO } from '../../services/booking.service';
import { AuthService } from '../../services/auth.service';

export interface CalendarDay {
  date: Date;
  isCurrentMonth: boolean;
  isToday: boolean;
  isPast: boolean;
  isOccupied: boolean;
  isSelectedCheckIn: boolean;
  isSelectedCheckOut: boolean;
  isInSelectedRange: boolean;
}

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

  // Calendar Signals
  occupiedRanges = signal<{ checkIn: string; checkOut: string }[]>([]);
  currentCalendarYear = signal<number>(new Date().getFullYear());
  currentCalendarMonth = signal<number>(new Date().getMonth());
  bookingStep = signal<1 | 2>(1);

  // Payment Form Fields
  cardHolder = signal<string>('');
  cardNumber = signal<string>('');
  cvv = signal<string>('');
  expirationDate = signal<string>('');
  paypalEmail = signal<string>('');
  paypalOrderId = signal<string>('');
  paypalSdkLoading = signal<boolean>(false);

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

  calendarMonthName = computed(() => {
    const months = [
      'Gennaio', 'Febbraio', 'Marzo', 'Aprile', 'Maggio', 'Giugno',
      'Luglio', 'Agosto', 'Settembre', 'Ottobre', 'Novembre', 'Dicembre'
    ];
    return `${months[this.currentCalendarMonth()]} ${this.currentCalendarYear()}`;
  });

  calendarDays = computed(() => {
    const year = this.currentCalendarYear();
    const month = this.currentCalendarMonth();
    const inStr = this.checkIn();
    const outStr = this.checkOut();
    const checkInDate = inStr ? new Date(inStr) : null;
    const checkOutDate = outStr ? new Date(outStr) : null;
    if (checkInDate) checkInDate.setHours(0, 0, 0, 0);
    if (checkOutDate) checkOutDate.setHours(0, 0, 0, 0);

    const today = new Date();
    today.setHours(0, 0, 0, 0);

    // Days in current month
    const firstDayOfMonth = new Date(year, month, 1);
    const lastDayOfMonth = new Date(year, month + 1, 0);
    
    // Day of week of first day: 0 (Sun) to 6 (Sat)
    // We want Monday (1) to Sunday (7)
    let startDayOfWeek = firstDayOfMonth.getDay();
    if (startDayOfWeek === 0) startDayOfWeek = 7;

    const days: CalendarDay[] = [];

    // Padding days from previous month
    const prevMonthLastDay = new Date(year, month, 0).getDate();
    for (let i = startDayOfWeek - 1; i > 0; i--) {
      const prevDate = new Date(year, month - 1, prevMonthLastDay - i + 1);
      prevDate.setHours(0, 0, 0, 0);
      days.push(this.createCalendarDay(prevDate, false, today, checkInDate, checkOutDate));
    }

    // Days of current month
    const totalDays = lastDayOfMonth.getDate();
    for (let i = 1; i <= totalDays; i++) {
      const currentDate = new Date(year, month, i);
      currentDate.setHours(0, 0, 0, 0);
      days.push(this.createCalendarDay(currentDate, true, today, checkInDate, checkOutDate));
    }

    // Padding days from next month to complete the grid (usually 42 cells or 6 rows)
    const remainingCells = 42 - days.length;
    for (let i = 1; i <= remainingCells; i++) {
      const nextDate = new Date(year, month + 1, i);
      nextDate.setHours(0, 0, 0, 0);
      days.push(this.createCalendarDay(nextDate, false, today, checkInDate, checkOutDate));
    }

    return days;
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
        this.errorMessage.set('Impossibile recuperare l\'elenco delle camere. Assicurati che il backend sia attivo.');
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
    this.checkIn.set('');
    this.checkOut.set('');
    this.bookingStep.set(1);

    // Reset forms & alerts
    this.errorMessage.set(null);
    this.successMessage.set(null);
    this.cardHolder.set('');
    this.cardNumber.set('');
    this.cvv.set('');
    this.expirationDate.set('');
    this.paypalEmail.set('');
    this.paypalOrderId.set('');
    this.paymentMethod.set('CLASSIC_CARD');

    // Reset touched states
    this.cardHolderTouched.set(false);
    this.cardNumberTouched.set(false);
    this.cvvTouched.set(false);
    this.expirationDateTouched.set(false);
    this.paypalEmailTouched.set(false);
    this.checkInTouched.set(false);
    this.checkOutTouched.set(false);

    // Reset calendar view to current month
    const now = new Date();
    this.currentCalendarYear.set(now.getFullYear());
    this.currentCalendarMonth.set(now.getMonth());

    if (room.id) {
      this.roomService.getOccupiedDates(room.id).subscribe({
        next: (ranges) => {
          this.occupiedRanges.set(ranges);
        },
        error: (err) => {
          console.error('Failed to load occupied dates:', err);
          this.occupiedRanges.set([]);
        }
      });
    }
  }

  goToPayment(): void {
    if (this.isDatesValid() && this.numberOfNights() > 0) {
      this.bookingStep.set(2);
      if (this.paymentMethod() === 'PAYPAL') {
        setTimeout(() => {
          this.initPaypalButtons();
        }, 0);
      }
    }
  }

  // Calendar Helper Methods
  private createCalendarDay(
    date: Date, 
    isCurrentMonth: boolean, 
    today: Date, 
    checkInDate: Date | null, 
    checkOutDate: Date | null
  ): CalendarDay {
    const dateTime = date.getTime();
    const todayTime = today.getTime();
    
    const isPast = dateTime < todayTime;
    const isOccupied = this.isDateOccupied(date);
    
    const isSelectedCheckIn = checkInDate !== null && dateTime === checkInDate.getTime();
    const isSelectedCheckOut = checkOutDate !== null && dateTime === checkOutDate.getTime();
    
    const isInSelectedRange = checkInDate !== null && checkOutDate !== null && 
                              dateTime > checkInDate.getTime() && dateTime < checkOutDate.getTime();

    return {
      date,
      isCurrentMonth,
      isToday: dateTime === todayTime,
      isPast,
      isOccupied,
      isSelectedCheckIn,
      isSelectedCheckOut,
      isInSelectedRange
    };
  }

  isDateOccupied(date: Date): boolean {
    const dTime = date.getTime();
    return this.occupiedRanges().some(range => {
      const inTime = new Date(range.checkIn).setHours(0, 0, 0, 0);
      const outTime = new Date(range.checkOut).setHours(0, 0, 0, 0);
      return dTime >= inTime && dTime < outTime;
    });
  }

  selectDate(day: CalendarDay): void {
    if (day.isPast || day.isOccupied) return;

    const inStr = this.checkIn();
    const outStr = this.checkOut();

    if (!inStr || (inStr && outStr)) {
      this.checkIn.set(this.formatDate(day.date));
      this.checkOut.set('');
      this.checkInTouched.set(true);
      this.checkOutTouched.set(false);
    } else {
      const checkInDate = new Date(inStr);
      const clickedDate = day.date;

      if (clickedDate.getTime() === checkInDate.getTime()) {
        this.checkIn.set('');
        this.checkOut.set('');
      } else if (clickedDate.getTime() < checkInDate.getTime()) {
        this.checkIn.set(this.formatDate(clickedDate));
      } else {
        if (this.hasOccupiedDatesBetween(checkInDate, clickedDate)) {
          this.checkIn.set(this.formatDate(clickedDate));
        } else {
          this.checkOut.set(this.formatDate(clickedDate));
          this.checkOutTouched.set(true);
        }
      }
    }
  }

  hasOccupiedDatesBetween(start: Date, end: Date): boolean {
    const endTime = end.getTime();
    let temp = new Date(start);
    temp.setDate(temp.getDate() + 1);
    while (temp.getTime() < endTime) {
      if (this.isDateOccupied(temp)) {
        return true;
      }
      temp.setDate(temp.getDate() + 1);
    }
    return false;
  }

  formatDate(d: Date): string {
    const year = d.getFullYear();
    const month = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  }

  prevMonth(): void {
    const currentMonth = this.currentCalendarMonth();
    const currentYear = this.currentCalendarYear();
    if (currentMonth === 0) {
      this.currentCalendarMonth.set(11);
      this.currentCalendarYear.set(currentYear - 1);
    } else {
      this.currentCalendarMonth.set(currentMonth - 1);
    }
  }

  nextMonth(): void {
    const currentMonth = this.currentCalendarMonth();
    const currentYear = this.currentCalendarYear();
    if (currentMonth === 11) {
      this.currentCalendarMonth.set(0);
      this.currentCalendarYear.set(currentYear + 1);
    } else {
      this.currentCalendarMonth.set(currentMonth + 1);
    }
  }

  setPaymentMethod(method: 'CLASSIC_CARD' | 'PAYPAL'): void {
    this.paymentMethod.set(method);
    if (method === 'PAYPAL') {
      setTimeout(() => {
        this.initPaypalButtons();
      }, 0);
    }
  }

  private loadPaypalSdk(): Promise<void> {
    return new Promise((resolve, reject) => {
      const win = window as any;
      if (win.paypal) {
        resolve();
        return;
      }
      
      this.bookingService.getPayPalClientId().subscribe({
        next: (config) => {
          const clientId = config.clientId || 'test';
          const script = document.createElement('script');
          script.src = `https://www.paypal.com/sdk/js?client-id=${clientId}&currency=EUR&intent=capture`;
          script.type = 'text/javascript';
          script.async = true;
          script.onload = () => resolve();
          script.onerror = (err) => reject(err);
          document.head.appendChild(script);
        },
        error: (err) => {
          console.warn('Failed to fetch PayPal Client ID, falling back to "test":', err);
          const script = document.createElement('script');
          script.src = 'https://www.paypal.com/sdk/js?client-id=test&currency=EUR&intent=capture';
          script.type = 'text/javascript';
          script.async = true;
          script.onload = () => resolve();
          script.onerror = (e) => reject(e);
          document.head.appendChild(script);
        }
      });
    });
  }

  initPaypalButtons(): void {
    if (!isPlatformBrowser(this.platformId)) return;

    this.paypalSdkLoading.set(true);
    this.errorMessage.set(null);

    this.loadPaypalSdk()
      .then(() => {
        this.paypalSdkLoading.set(false);
        const container = document.getElementById('paypal-button-container');
        if (container) {
          container.innerHTML = '';
        }

        const paypal = (window as any).paypal;
        if (paypal && paypal.Buttons) {
          paypal.Buttons({
            style: {
              layout: 'vertical',
              color: 'gold',
              shape: 'rect',
              label: 'paypal'
            },
            createOrder: (data: any, actions: any) => {
              const price = this.totalPrice();
              return actions.order.create({
                purchase_units: [{
                  amount: {
                    currency_code: 'EUR',
                    value: price.toFixed(2)
                  },
                  description: `StayHub Booking for ${this.selectedRoom()?.name}`
                }]
              });
            },
            onApprove: (data: any, actions: any) => {
              this.paypalOrderId.set(data.orderID);
              this.submitBooking();
            },
            onCancel: (data: any) => {
              this.errorMessage.set('Pagamento annullato dall\'utente.');
            },
            onError: (err: any) => {
              console.error('PayPal Smart Buttons error:', err);
              this.errorMessage.set('Si è verificato un errore durante il pagamento con PayPal. Riprova.');
            }
          }).render('#paypal-button-container');
        } else {
          this.errorMessage.set('Impossibile inizializzare l\'SDK di PayPal.');
        }
      })
      .catch(err => {
        console.error('Failed to load PayPal SDK script:', err);
        this.paypalSdkLoading.set(false);
        this.errorMessage.set('Impossibile caricare l\'SDK di PayPal. Controlla la tua connessione internet.');
      });
  }

  closeBookingModal(): void {
    this.selectedRoom.set(null);
    this.paypalOrderId.set('');
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
        paypalEmail: this.paypalEmail() || 'paypal-customer@stayhub.com',
        paypalOrderId: this.paypalOrderId() || 'MOCK-PAY-' + Math.random().toString(36).substring(2, 9).toUpperCase()
      }
    };

    this.bookingService.createBooking(request).subscribe({
      next: (response) => {
        this.loading.set(false);
        this.successMessage.set('Prenotazione confermata e pagata con successo!');
        setTimeout(() => {
          this.closeBookingModal();
        }, 2500);
      },
      error: (err) => {
        this.loading.set(false);
        console.error('Booking failed:', err);
        const errMsg = err.error?.message || 'Prenotazione fallita. Controlla i tuoi dati e riprova.';
        this.errorMessage.set(errMsg);
      }
    });
  }
}
