import { describe, expect, it, vi } from 'vitest';

const http = { isResponseError: vi.fn(), latestResponse: { statusText: 'Forbidden' } };
vi.mock('@open-ent/client', () => ({ odeServices: { http: () => http } }));

import { guardWikiService } from '~/services/api';

const service = guardWikiService({
  getPage: async () => ({ title: 'x' }),
  deletePage: async () => ({ ignored: true }),
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
});
