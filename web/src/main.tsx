import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
// The app's three faces (docs/design.md §1), served from the site itself rather
// than a font host: a page about keeping things private shouldn't hand its
// visitors to a third party.
import '@fontsource/young-serif/latin-400.css'
import '@fontsource-variable/instrument-sans/wght.css'
import '@fontsource/ibm-plex-mono/latin-400.css'
import '@fontsource/ibm-plex-mono/latin-500.css'
import './index.css'
import App from './App.tsx'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
