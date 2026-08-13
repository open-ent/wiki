import React from 'react';

import { createRoot } from 'react-dom/client';

import { EdificeThemeProvider } from '@open-ent/react';
import { RouterProvider } from 'react-router-dom';
import './i18n';
import { Providers, queryClient } from './providers';
import { router } from './routes';

// Le bootstrap openent n'est plus bundlé : il est chargé au runtime via
// <link href="/assets/themes/openent-bootstrap/index.css"> dans index.html
// (cf. README-THEME). Permet de changer le look sans recompiler le module.

const rootElement = document.getElementById('root');
const root = createRoot(rootElement!);

if (import.meta.env.DEV) {
  import('@axe-core/react').then((axe) => {
    axe.default(React, root, 1000);
  });
}

root.render(
  <Providers>
    <EdificeThemeProvider>
      <RouterProvider router={router(queryClient)} />
    </EdificeThemeProvider>
  </Providers>,
);
