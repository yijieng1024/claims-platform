import { WritableSignal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { DocumentControllerService } from '../../api/claims';

/** Thrown when one file fails; says which file and carries the HTTP error. */
export class UploadError extends Error {
  constructor(
    readonly file: File,
    readonly httpError: unknown,
  ) {
    super(`Upload of ${file.name} failed`);
  }
}

/**
 * Uploads the files one at a time and stops at the first failure.
 * Each file that uploaded is removed from `files`, so a retry only sends the ones still listed.
 * `informationRequestId` links the documents to the claimant's answer to an information request.
 */
export async function uploadFiles(
  api: DocumentControllerService,
  claimId: string,
  files: WritableSignal<File[]>,
  informationRequestId?: string,
): Promise<void> {
  for (const file of files()) {
    try {
      await firstValueFrom(api.upload(claimId, file, informationRequestId));
    } catch (err) {
      throw new UploadError(file, err);
    }
    files.update((list) => list.filter((f) => f !== file));
  }
}
