import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

export const authGuard: CanActivateFn = (route, state) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (authService.isAuthenticated()) {
    const requiredRole = route.data['role'];
    if (requiredRole && !authService.hasRole(requiredRole)) {
      router.navigate(['/']);
      return false;
    }
    return true;
  }

  authService.login();
  return false;
};
