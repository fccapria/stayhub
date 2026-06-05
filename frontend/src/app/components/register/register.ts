import { Component, inject, signal, computed, PLATFORM_ID } from '@angular/core';
import { CommonModule, isPlatformBrowser } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { AuthService } from '../../services/auth.service';

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

  // Form Signals
  username = signal<string>('');
  password = signal<string>('');
  email = signal<string>('');
  firstName = signal<string>('');
  lastName = signal<string>('');
  role = signal<'CUSTOMER' | 'ADMIN'>('CUSTOMER');

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

  // Validation Computeds
  isUsernameValid = computed(() => this.username().trim().length >= 3);
  isPasswordValid = computed(() => this.password().trim().length >= 6);
  isEmailValid = computed(() => 
    /^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$/.test(this.email().trim())
  );
  isFirstNameValid = computed(() => this.firstName().trim().length >= 1);
  isLastNameValid = computed(() => this.lastName().trim().length >= 1);

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

    this.http.post('http://localhost:8080/api/v1/users/register', payload).subscribe({
      next: () => {
        this.loading.set(false);
        this.successMessage.set('Account created successfully! Redirecting you to login...');
        
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
        const errMsg = err.error?.message || 'Could not create account. The username or email might already be taken.';
        this.errorMessage.set(errMsg);
      }
    });
  }
}
