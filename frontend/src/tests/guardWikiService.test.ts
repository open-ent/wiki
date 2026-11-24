import { odeServices } from '@open-ent/client';
import { afterAll, beforeAll, describe, expect, it, vi } from 'vitest';

import { guardWikiService } from '~/services/api';

// Espion sur l'instance plutôt que vi.mock du module : le setup des tests (providers) charge déjà
// ~/services/api avec le vrai @open-ent/client, un vi.mock arriverait après coup et serait ignoré.
const http = {
  isResponseError: vi.fn(),
  latestResponse: { statusText: 'Forbidden' },
};
let httpSpy: ReturnType<typeof vi.spyOn>;
beforeAll(() => {
  httpSpy = vi
    .spyOn(odeServices, 'http')
    .mockReturnValue(http as unknown as ReturnType<typeof odeServices.http>);
});
afterAll(() => httpSpy.mockRestore());

const service = guardWikiService({
  getPage: async () => ({ title: 'x' }),
  deletePage: async () => ({ ignored: true }),
});

// Appels qui REJETTENT (ex. PUT de page en 500 rejeté avec le corps `{}` par le client du socle)
const rejecting = guardWikiService({
  getWiki: async () => Promise.reject({}),
  updatePage: async () => Promise.reject({}),
});

describe('guardWikiService', () => {
  it('laisse passer les réponses valides', async () => {
    http.isResponseError.mockReturnValue(false);
    await expect(service.getPage()).resolves.toEqual({ title: 'x' });
    await expect(service.deletePage()).resolves.toEqual({ ignored: true });
  });

  it('une lecture en erreur lève (ex. page lue en 403)', async () => {
    http.isResponseError.mockReturnValue(true);
    await expect(service.getPage()).rejects.toThrow('Forbidden');
  });

  it('une écriture en erreur renvoie { error } (ex. suppression refusée)', async () => {
    http.isResponseError.mockReturnValue(true);
    await expect(service.deletePage()).resolves.toEqual({ error: 'Forbidden' });
  });

  it('une écriture qui rejette renvoie { error } au lieu de lever (ex. PUT de page en 500)', async () => {
    http.isResponseError.mockReturnValue(true);
    http.latestResponse = { statusText: 'Internal Server Error' };
    await expect(rejecting.updatePage()).resolves.toEqual({ error: 'Internal Server Error' });
  });

  it('une lecture qui rejette lève une Error', async () => {
    http.latestResponse = { statusText: 'Internal Server Error' };
    await expect(rejecting.getWiki()).rejects.toThrow('Internal Server Error');
  });
});
