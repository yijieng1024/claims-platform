export * from './claim-controller.service';
import { ClaimControllerService } from './claim-controller.service';
export * from './document-controller.service';
import { DocumentControllerService } from './document-controller.service';
export * from './policy-controller.service';
import { PolicyControllerService } from './policy-controller.service';
export const APIS = [ClaimControllerService, DocumentControllerService, PolicyControllerService];
