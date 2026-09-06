import { inject, PLATFORM_ID } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

export const authGuard: CanActivateFn = (route, state) => {
  const platformId = inject(PLATFORM_ID);
  
  // Durante il rendering SSR lato server, consentiamo il rendering della shell HTML iniziale
  if (!isPlatformBrowser(platformId)) {
    return true;
  }

  const authService = inject(AuthService);
  const router = inject(Router);

  if (authService.isAuthenticated()) {
    const requiredRoles = route.data['roles'] as string[] | undefined;
    const requiredRole = route.data['role'] as string | undefined;

    if (requiredRoles && requiredRoles.length > 0) {
      const hasAny = requiredRoles.some(r => authService.hasRole(r));
      if (!hasAny) {
        router.navigate(['/']);
        return false;
      }
    } else if (requiredRole && !authService.hasRole(requiredRole)) {
      router.navigate(['/']);
      return false;
    }
    return true;
  }

  authService.login();
  return false;
};
