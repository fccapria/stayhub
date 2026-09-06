import { Component, inject, signal, computed, PLATFORM_ID } from '@angular/core';
import { CommonModule, isPlatformBrowser } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { environment } from '../../../environments/environment';

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './register.html',
  styleUrl: './register.css'
})
export class RegisterComponent {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  protected readonly authService = inject(AuthService);
  private readonly platformId = inject(PLATFORM_ID);

  ngOnInit(): void {
    if (isPlatformBrowser(this.platformId) && this.authService.isAuthenticated()) {
      this.router.navigate(['/']);
    }
  }

  // Form Signals
  username = signal<string>('');
  password = signal<string>('');
  email = signal<string>('');
  firstName = signal<string>('');
  lastName = signal<string>('');
  role = signal<'CUSTOMER' | 'HOST'>('CUSTOMER');

  // Touched States
  usernameTouched = signal<boolean>(false);
  passwordTouched = signal<boolean>(false);
  emailTouched = signal<boolean>(false);
  firstNameTouched = signal<boolean>(false);
  lastNameTouched = signal<boolean>(false);

  // Status flags
  loading = signal<boolean>(false);
  errorMessage = signal<string | null>(null);
  successMessage = signal<string | null>(null);
  showPassword = signal<boolean>(false);

  // Validation Patterns & Computeds
  readonly usernamePattern = /^[a-zA-Z0-9._-]+$/;
  readonly emailPattern = /^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$/;
  readonly namePattern = /^[a-zA-Z\u00C0-\u024F\s'-]+$/;

  isUsernameFormatValid = computed(() => {
    const u = this.username().trim();
    return u.length === 0 || this.usernamePattern.test(u);
  });
  isUsernameValid = computed(() => {
    const u = this.username().trim();
    return u.length >= 3 && u.length <= 30 && this.usernamePattern.test(u);
  });

  isPasswordValid = computed(() => {
    const p = this.password();
    return p.length >= 6 && p.trim().length >= 6 && p.length <= 100;
  });

  isEmailValid = computed(() => {
    const e = this.email().trim();
    return e.length >= 5 && e.length <= 100 && this.emailPattern.test(e);
  });

  isFirstNameFormatValid = computed(() => {
    const val = this.firstName().trim();
    return val.length === 0 || (val.length <= 50 && this.namePattern.test(val));
  });
  isFirstNameValid = computed(() => {
    const val = this.firstName().trim();
    return val.length >= 1 && val.length <= 50 && this.namePattern.test(val);
  });

  isLastNameFormatValid = computed(() => {
    const val = this.lastName().trim();
    return val.length === 0 || (val.length <= 50 && this.namePattern.test(val));
  });
  isLastNameValid = computed(() => {
    const val = this.lastName().trim();
    return val.length >= 1 && val.length <= 50 && this.namePattern.test(val);
  });

  usernameConflict = computed(() => {
    const msg = this.errorMessage()?.toLowerCase() || '';
    return msg.includes('nome utente') || msg.includes('username');
  });

  emailConflict = computed(() => {
    const msg = this.errorMessage()?.toLowerCase() || '';
    return msg.includes('email');
  });

  isFormValid = computed(() => {
    return this.isUsernameValid() &&
           this.isPasswordValid() &&
           this.isEmailValid() &&
           this.isFirstNameValid() &&
           this.isLastNameValid();
  });

  submitRegister(): void {
    if (!this.isFormValid()) return;

    this.loading.set(true);
    this.errorMessage.set(null);
    this.successMessage.set(null);

    const payload = {
      username: this.username().trim(),
      password: this.password().trim(),
      email: this.email().trim(),
      firstName: this.firstName().trim(),
      lastName: this.lastName().trim(),
      role: this.role()
    };

    this.http.post(`${environment.apiUrl}/users/register`, payload).subscribe({
      next: () => {
        this.loading.set(false);
        this.successMessage.set('Account creato con successo! Reindirizzamento al login...');
        
        setTimeout(() => {
          if (isPlatformBrowser(this.platformId)) {
            // Automatically trigger login redirect so they can sign in with their new credentials
            this.authService.login();
          }
        }, 2000);
      },
      error: (err) => {
        this.loading.set(false);
        console.error('Registration failed:', err);
        let errMsg = err.error?.message;
        if (!errMsg && typeof err.error === 'string') {
          try {
            const parsed = JSON.parse(err.error);
            errMsg = parsed.message || parsed.errorMessage;
          } catch {
            errMsg = err.error;
          }
        }
        if (!errMsg) {
          if (err.status === 409) {
            errMsg = 'Nome utente o indirizzo email già registrati.';
          } else {
            errMsg = 'Impossibile completare la registrazione. Riprova più tardi.';
          }
        }
        this.errorMessage.set(errMsg);
      }
    });
  }
}
