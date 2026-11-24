export * from './auth-controller.service';
import { AuthControllerService } from './auth-controller.service';
export * from './jwks-controller.service';
import { JwksControllerService } from './jwks-controller.service';
export const APIS = [AuthControllerService, JwksControllerService];
