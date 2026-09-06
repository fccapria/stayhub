import { Routes } from '@angular/router';
import { RoomListComponent } from './components/room-list/room-list';
import { BookingListComponent } from './components/booking-list/booking-list';
import { AdminDashboardComponent } from './components/admin-dashboard/admin-dashboard';
import { RegisterComponent } from './components/register/register';
import { authGuard } from './guards/auth.guard';

export const routes: Routes = [
  { path: '', component: RoomListComponent },
  { path: 'register', component: RegisterComponent },
  { 
    path: 'bookings/my', 
    component: BookingListComponent, 
    canActivate: [authGuard]
  },
  { 
    path: 'admin', 
    component: AdminDashboardComponent, 
    canActivate: [authGuard], 
    data: { roles: ['ADMIN', 'HOST'] } 
  },
  { 
    path: 'host', 
    redirectTo: 'admin', 
    pathMatch: 'full' 
  },
  { path: '**', redirectTo: '' }
];

