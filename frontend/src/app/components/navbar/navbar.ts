import { Component, inject, signal } from '@angular/core';
import { RouterLink, RouterLinkActive, Router } from '@angular/router';
import { NgIf } from '@angular/common';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-navbar',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, NgIf],
  templateUrl: './navbar.html',
  styleUrl: './navbar.css'
})
export class Navbar {
  protected readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  becomingHost = signal<boolean>(false);

  login(): void {
    this.authService.login();
  }

  logout(): void {
    this.authService.logout();
  }

  get username(): string | null {
    return this.authService.getUsername();
  }

  get isAuthenticated(): boolean {
    return this.authService.isAuthenticated();
  }

  get isAdmin(): boolean {
    return this.authService.isAdmin();
  }

  get isHost(): boolean {
    return this.authService.isHost();
  }

  get isHostOrAdmin(): boolean {
    return this.authService.isHostOrAdmin();
  }

  async becomeHost(): Promise<void> {
    if (this.becomingHost()) return;
    this.becomingHost.set(true);
    try {
      const ok = await this.authService.becomeHost();
      if (ok) {
        this.router.navigate(['/admin']);
      } else {
        alert('Impossibile abilitare la modalità Locatore. Riprova più tardi.');
      }
    } finally {
      this.becomingHost.set(false);
    }
  }
}
