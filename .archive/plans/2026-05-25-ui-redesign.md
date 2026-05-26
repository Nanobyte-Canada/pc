# UI Redesign — Verdant Dark Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the current warm brown/cool blue theme with the Verdant Dark design system — emerald-on-dark, DM Sans + JetBrains Mono, mobile-first with icon rail navigation on desktop and bottom tab bar on mobile.

**Architecture:** This is a CSS-first redesign. The design tokens (custom properties) in `index.css` drive all visual changes. Navigation is rebuilt from sidebar to icon-rail+flyout (desktop) and bottom-tab-bar (mobile). Each screen is then restyled to match the new design system. No backend changes.

**Tech Stack:** React 18, TypeScript, Vite, plain CSS with custom properties, AG Grid, Lucide React icons, Zustand stores.

**Spec:** `docs/superpowers/specs/2026-05-25-ui-redesign-design.md`

---

## Phase 1: Design System Foundation

### Task 1: Update Fonts

**Files:**
- Modify: `frontend/index.html`
- Modify: `frontend/src/index.css:152-157` (body font-family)

- [ ] **Step 1: Update Google Fonts link in index.html**

Replace the existing Manrope font link with DM Sans + JetBrains Mono:

```html
<link href="https://fonts.googleapis.com/css2?family=DM+Sans:ital,opsz,wght@0,9..40,100..1000;1,9..40,100..1000&family=JetBrains+Mono:wght@400;500;600;700&display=swap" rel="stylesheet">
```

Remove the existing Manrope link. Keep the JetBrains Mono link if already present (it is — but update to include weights 400-700).

- [ ] **Step 2: Update body font-family in index.css**

Replace:
```css
font-family: 'Manrope', -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Roboto', 'Oxygen',
    'Ubuntu', 'Cantarell', 'Fira Sans', 'Droid Sans', 'Helvetica Neue',
    sans-serif;
```

With:
```css
font-family: 'DM Sans', -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Roboto', 'Oxygen',
    'Ubuntu', 'Cantarell', 'Fira Sans', 'Droid Sans', 'Helvetica Neue',
    sans-serif;
```

- [ ] **Step 3: Run build to verify no errors**

Run: `cd frontend && npm run build`
Expected: Build succeeds with no errors.

- [ ] **Step 4: Commit**

```bash
git add frontend/index.html frontend/src/index.css
git commit -m "feat(ui): replace Manrope with DM Sans, update JetBrains Mono weights"
```

---

### Task 2: Replace Color Tokens — Dark Mode

**Files:**
- Modify: `frontend/src/index.css:81-150` (html.dark block)

- [ ] **Step 1: Replace the entire `html.dark` block**

Replace the existing `html.dark { ... }` block with the new Verdant Dark palette:

```css
html.dark {
  --bg-primary: #0a0f1a;
  --bg-secondary: #111827;
  --bg-tertiary: #1a2332;
  --bg-surface: #0f1520;
  --card-bg: #111827;
  --text-primary: #e2e8f0;
  --text-secondary: #94a3b8;
  --text-muted: #64748b;
  --accent: #10b981;
  --accent-hover: #059669;
  --accent-light: rgba(16,185,129,0.15);
  --accent-secondary: #059669;
  --accent-secondary-hover: #047857;
  --accent-subtle: rgba(16,185,129,0.08);
  --accent-border: rgba(16,185,129,0.15);
  --border: rgba(255,255,255,0.06);
  --border-hover: rgba(255,255,255,0.1);
  --nav-bg: #0f1520;
  --nav-text: #e2e8f0;
  --error: #f87171;
  --success: #10b981;
  --warning: #fbbf24;
  --info: #60a5fa;
  --info-hover: #3b82f6;
  --input-bg: rgba(255,255,255,0.04);
  --input-border: rgba(255,255,255,0.06);
  --ring: #10b981;
  --popover-bg: #111827;
  --popover-text: #e2e8f0;
  --destructive: #dc2626;
  --secondary-bg: #1a2332;
  --secondary-text: #e2e8f0;
  --muted-bg: rgba(255,255,255,0.04);
  --model-badge-bg: rgba(16,185,129,0.12);
  --model-badge-text: #6ee7b7;
  --error-dark: #fca5a5;
  --warning-dark: #fde68a;
  --success-dark: #6ee7b7;

  --csp: #6366f1;
  --csp-bg: rgba(99,102,241,0.08);
  --csp-border: rgba(99,102,241,0.3);
  --cc: #f97316;
  --cc-bg: rgba(249,115,22,0.08);
  --cc-border: rgba(249,115,22,0.3);

  --success-light: rgba(16,185,129,0.12);
  --success-text: #6ee7b7;
  --danger-light: rgba(248,113,113,0.12);
  --danger-text: #f87171;
  --warning-light: rgba(251,191,36,0.12);
  --warning-text: #fbbf24;
  --info-light: rgba(96,165,250,0.12);
  --info-text: #60a5fa;
  --cyan-light: rgba(34,211,238,0.12);
  --cyan-text: #22d3ee;
  --purple-light: rgba(167,139,250,0.12);
  --purple-text: #a78bfa;
  --orange-light: rgba(249,115,22,0.12);
  --orange-text: #fb923c;
  --indigo-light: rgba(99,102,241,0.12);
  --indigo-text: #818cf8;
  --amber-light: rgba(251,191,36,0.12);
  --amber-text: #fcd34d;
  --red-light: rgba(248,113,113,0.12);
  --red-text: #f87171;
  --blue-light: rgba(96,165,250,0.12);
  --blue-text: #60a5fa;
  --teal-light: rgba(94,234,212,0.12);
  --teal-text: #5eead4;
  --green-light: rgba(16,185,129,0.12);
  --green-text: #6ee7b7;
  --yellow-light: rgba(250,204,21,0.12);
  --yellow-text: #facc15;
  --gray-light: rgba(148,163,184,0.12);
  --gray-text: #94a3b8;
  --gray-stroke: rgba(255,255,255,0.08);
}
```

- [ ] **Step 2: Run build**

Run: `cd frontend && npm run build`
Expected: Build succeeds.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/index.css
git commit -m "feat(ui): replace dark mode tokens with Verdant Dark emerald palette"
```

---

### Task 3: Replace Color Tokens — Light Mode

**Files:**
- Modify: `frontend/src/index.css:12-79` (:root block)

- [ ] **Step 1: Replace the `:root` block**

Replace the existing `:root { ... }` custom properties with the Verdant Dark light mode palette:

```css
:root {
  --bg-primary: #f8fafb;
  --bg-secondary: #ffffff;
  --bg-tertiary: #f1f5f9;
  --bg-surface: #f8fafb;
  --card-bg: #ffffff;
  --text-primary: #0f172a;
  --text-secondary: #475569;
  --text-muted: #94a3b8;
  --accent: #059669;
  --accent-hover: #047857;
  --accent-light: rgba(5,150,105,0.1);
  --accent-secondary: #047857;
  --accent-secondary-hover: #065f46;
  --accent-subtle: rgba(5,150,105,0.06);
  --accent-border: rgba(5,150,105,0.15);
  --border: #e2e8f0;
  --border-hover: #cbd5e1;
  --nav-bg: #f8fafb;
  --nav-text: #0f172a;
  --error: #dc2626;
  --success: #059669;
  --warning: #d97706;
  --info: #3b82f6;
  --info-hover: #2563eb;
  --input-bg: #ffffff;
  --input-border: #e2e8f0;
  --ring: #059669;
  --popover-bg: #ffffff;
  --popover-text: #0f172a;
  --destructive: #dc2626;
  --secondary-bg: #f1f5f9;
  --secondary-text: #0f172a;
  --muted-bg: #f1f5f9;
  --model-badge-bg: rgba(5,150,105,0.1);
  --model-badge-text: #059669;
  --error-dark: #991b1b;
  --warning-dark: #92400e;
  --success-dark: #065f46;

  --csp: #4f46e5;
  --csp-bg: rgba(79,70,229,0.06);
  --csp-border: rgba(79,70,229,0.2);
  --cc: #ea580c;
  --cc-bg: rgba(234,88,12,0.06);
  --cc-border: rgba(234,88,12,0.2);

  --success-light: #dcfce7;
  --success-text: #059669;
  --danger-light: #fee2e2;
  --danger-text: #dc2626;
  --warning-light: #fef3c7;
  --warning-text: #d97706;
  --info-light: #dbeafe;
  --info-text: #2563eb;
  --cyan-light: #cffafe;
  --cyan-text: #0891b2;
  --purple-light: #f3e8ff;
  --purple-text: #7c3aed;
  --orange-light: #ffedd5;
  --orange-text: #ea580c;
  --indigo-light: #e0e7ff;
  --indigo-text: #4f46e5;
  --amber-light: #fef3c7;
  --amber-text: #d97706;
  --red-light: #fee2e2;
  --red-text: #dc2626;
  --blue-light: #dbeafe;
  --blue-text: #2563eb;
  --teal-light: #ccfbf1;
  --teal-text: #0d9488;
  --green-light: #dcfce7;
  --green-text: #059669;
  --yellow-light: #fef9c3;
  --yellow-text: #ca8a04;
  --gray-light: #f1f5f9;
  --gray-text: #64748b;
  --gray-stroke: #e2e8f0;
}
```

- [ ] **Step 2: Run build and lint**

Run: `cd frontend && npm run build && npm run lint`
Expected: Both pass.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/index.css
git commit -m "feat(ui): replace light mode tokens with Verdant Dark palette"
```

---

### Task 4: Add Motion Tokens and Typography Scale

**Files:**
- Modify: `frontend/src/index.css` (add after `:root` block)

- [ ] **Step 1: Add motion and typography tokens**

Add these CSS custom properties inside both `:root` and `html.dark` blocks (they're the same in both themes):

```css
  /* Motion */
  --transition-fast: 150ms ease-out;
  --transition-base: 200ms ease-out;
  --transition-slow: 300ms ease-out;
  --ease-out-expo: cubic-bezier(0.16, 1, 0.3, 1);

  /* Typography scale */
  --font-mono: 'JetBrains Mono', 'source-code-pro', Menlo, Monaco, Consolas, monospace;
  --text-xs: 0.75rem;
  --text-sm: 0.8125rem;
  --text-base: 0.875rem;
  --text-md: 1rem;
  --text-lg: 1.25rem;
  --text-xl: 1.5rem;
  --text-2xl: 2rem;
  --text-3xl: 2.5rem;

  /* Radius */
  --radius-sm: 6px;
  --radius-md: 10px;
  --radius-lg: 12px;
  --radius-xl: 16px;
  --radius-full: 9999px;
```

- [ ] **Step 2: Add reduced-motion media query**

Add at the end of `index.css`:

```css
@media (prefers-reduced-motion: reduce) {
  *, *::before, *::after {
    animation-duration: 0.01ms !important;
    animation-iteration-count: 1 !important;
    transition-duration: 0.01ms !important;
  }
}
```

- [ ] **Step 3: Update body base font size to 14px**

In the existing `body` rule, add/update:
```css
body {
  font-size: 0.875rem;
  line-height: 1.6;
}
```

- [ ] **Step 4: Run build**

Run: `cd frontend && npm run build`
Expected: Build succeeds.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/index.css
git commit -m "feat(ui): add motion tokens, typography scale, radius tokens, reduced-motion support"
```

---

### Task 5: Update AG Grid Theme Overrides

**Files:**
- Modify: `frontend/src/index.css` (AG Grid section)

- [ ] **Step 1: Find and update AG Grid theme CSS**

Search for `.ag-theme-quartz` in `index.css` and update the overrides to use new tokens:

```css
.ag-theme-quartz,
.ag-theme-quartz-dark {
  --ag-background-color: var(--bg-secondary);
  --ag-header-background-color: var(--bg-tertiary);
  --ag-odd-row-background-color: transparent;
  --ag-row-hover-color: var(--bg-tertiary);
  --ag-border-color: var(--border);
  --ag-header-foreground-color: var(--text-muted);
  --ag-foreground-color: var(--text-primary);
  --ag-secondary-foreground-color: var(--text-secondary);
  --ag-font-family: 'DM Sans', sans-serif;
  --ag-font-size: 13px;
  --ag-row-height: 42px;
  --ag-header-height: 40px;
  --ag-cell-horizontal-padding: 12px;
  --ag-borders: none;
  --ag-row-border-color: var(--border);
  --ag-header-column-separator-display: none;
  --ag-range-selection-border-color: var(--accent);
}

.ag-theme-quartz .ag-header-cell-label,
.ag-theme-quartz-dark .ag-header-cell-label {
  text-transform: uppercase;
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 0.5px;
}
```

- [ ] **Step 2: Run build**

Run: `cd frontend && npm run build`
Expected: Build succeeds.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/index.css
git commit -m "feat(ui): update AG Grid theme to Verdant Dark tokens"
```

---

## Phase 2: Navigation Overhaul

### Task 6: Build Icon Rail Component (Desktop)

**Files:**
- Create: `frontend/src/components/layout/IconRail.tsx`
- Create: `frontend/src/components/layout/IconRail.css`

- [ ] **Step 1: Create IconRail.css**

```css
.icon-rail {
  width: 56px;
  background: var(--bg-surface);
  border-right: 1px solid var(--border);
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 16px 0;
  gap: 4px;
  flex-shrink: 0;
}

.icon-rail__logo {
  width: 32px;
  height: 32px;
  border-radius: var(--radius-sm);
  background: linear-gradient(135deg, #10b981, #059669);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  color: white;
  font-weight: 700;
  margin-bottom: 16px;
}

.icon-rail__item {
  width: 40px;
  height: 40px;
  border-radius: var(--radius-sm);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-muted);
  cursor: pointer;
  position: relative;
  transition: background var(--transition-fast), color var(--transition-fast);
}

.icon-rail__item:hover {
  background: var(--bg-tertiary);
  color: var(--text-secondary);
}

.icon-rail__item--active {
  background: rgba(16, 185, 129, 0.1);
  color: var(--accent);
}

.icon-rail__item--active::before {
  content: '';
  position: absolute;
  left: 0;
  top: 10px;
  bottom: 10px;
  width: 2px;
  border-radius: 0 2px 2px 0;
  background: var(--accent);
}

.icon-rail__spacer {
  flex: 1;
}

.icon-rail__avatar {
  width: 28px;
  height: 28px;
  border-radius: var(--radius-full);
  background: linear-gradient(135deg, #10b981, #059669);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 10px;
  color: white;
  font-weight: 600;
  cursor: pointer;
}

.icon-rail__separator {
  width: 24px;
  height: 1px;
  background: var(--border);
  margin: 4px 0;
}
```

- [ ] **Step 2: Create IconRail.tsx**

```tsx
import { useLocation, useNavigate } from 'react-router-dom'
import {
  LayoutGrid, Layers, ClipboardList, Clock,
  Sun, Moon, Settings
} from 'lucide-react'
import { useThemeStore } from '@/stores/themeStore'
import { useAuthStore } from '@/stores/authStore'
import './IconRail.css'

interface NavItem {
  icon: React.ElementType
  path: string
  label: string
}

const navItems: NavItem[] = [
  { icon: LayoutGrid, path: '/', label: 'Dashboard' },
  { icon: Layers, path: '/brokers', label: 'Accounts' },
  { icon: ClipboardList, path: '/screener', label: 'Screener' },
  { icon: Clock, path: '/options', label: 'Options' },
]

export function IconRail() {
  const location = useLocation()
  const navigate = useNavigate()
  const { theme, toggleTheme } = useThemeStore()
  const user = useAuthStore((s) => s.user)

  const isActive = (path: string) => {
    if (path === '/') return location.pathname === '/'
    return location.pathname.startsWith(path)
  }

  const initials = user
    ? `${user.firstName?.[0] ?? ''}${user.lastName?.[0] ?? ''}`.toUpperCase()
    : '?'

  return (
    <nav className="icon-rail" aria-label="Main navigation">
      <div className="icon-rail__logo">P</div>

      {navItems.map((item) => (
        <button
          key={item.path}
          className={`icon-rail__item${isActive(item.path) ? ' icon-rail__item--active' : ''}`}
          onClick={() => navigate(item.path)}
          title={item.label}
          aria-label={item.label}
          aria-current={isActive(item.path) ? 'page' : undefined}
        >
          <item.icon size={18} />
        </button>
      ))}

      <div className="icon-rail__spacer" />

      <button
        className="icon-rail__item"
        onClick={toggleTheme}
        title={theme === 'dark' ? 'Switch to light mode' : 'Switch to dark mode'}
        aria-label="Toggle theme"
      >
        {theme === 'dark' ? <Sun size={18} /> : <Moon size={18} />}
      </button>

      <button
        className="icon-rail__item"
        onClick={() => navigate('/admin')}
        title="Admin"
        aria-label="Admin settings"
      >
        <Settings size={18} />
      </button>

      <button
        className="icon-rail__avatar"
        onClick={() => navigate('/profile')}
        title="Profile"
        aria-label="User profile"
      >
        {initials}
      </button>
    </nav>
  )
}
```

- [ ] **Step 3: Run build**

Run: `cd frontend && npm run build`
Expected: Build succeeds (component not yet used).

- [ ] **Step 4: Commit**

```bash
git add frontend/src/components/layout/IconRail.tsx frontend/src/components/layout/IconRail.css
git commit -m "feat(ui): create IconRail component for desktop navigation"
```

---

### Task 7: Build Bottom Tab Bar Component (Mobile)

**Files:**
- Create: `frontend/src/components/layout/BottomTabBar.tsx`
- Create: `frontend/src/components/layout/BottomTabBar.css`

- [ ] **Step 1: Create BottomTabBar.css**

```css
.bottom-tab-bar {
  display: flex;
  align-items: center;
  justify-content: space-around;
  height: 64px;
  background: var(--bg-surface);
  border-top: 1px solid var(--border);
  padding: 0 8px;
  flex-shrink: 0;
}

.bottom-tab-bar__item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 3px;
  background: none;
  border: none;
  cursor: pointer;
  padding: 4px 8px;
  min-width: 44px;
  min-height: 44px;
  justify-content: center;
  color: var(--text-muted);
  transition: color var(--transition-fast);
}

.bottom-tab-bar__item--active {
  color: var(--accent);
}

.bottom-tab-bar__label {
  font-size: 9px;
  font-weight: 600;
  font-family: 'DM Sans', sans-serif;
}

@media (min-width: 768px) {
  .bottom-tab-bar {
    display: none;
  }
}
```

- [ ] **Step 2: Create BottomTabBar.tsx**

```tsx
import { useLocation, useNavigate } from 'react-router-dom'
import { LayoutGrid, Layers, Search, Clock, Menu } from 'lucide-react'
import './BottomTabBar.css'

interface TabItem {
  icon: React.ElementType
  path: string
  label: string
}

const tabs: TabItem[] = [
  { icon: LayoutGrid, path: '/', label: 'Home' },
  { icon: Layers, path: '/brokers/connections', label: 'Accounts' },
  { icon: Search, path: '/screener/stocks', label: 'Screener' },
  { icon: Clock, path: '/options', label: 'Options' },
  { icon: Menu, path: '/more', label: 'More' },
]

export function BottomTabBar() {
  const location = useLocation()
  const navigate = useNavigate()

  const isActive = (path: string) => {
    if (path === '/') return location.pathname === '/'
    if (path === '/more') {
      return ['/wheel', '/brokers/reporting', '/admin', '/profile'].some(
        (p) => location.pathname.startsWith(p)
      )
    }
    return location.pathname.startsWith(path)
  }

  return (
    <nav className="bottom-tab-bar" aria-label="Tab navigation">
      {tabs.map((tab) => (
        <button
          key={tab.path}
          className={`bottom-tab-bar__item${isActive(tab.path) ? ' bottom-tab-bar__item--active' : ''}`}
          onClick={() => navigate(tab.path)}
          aria-label={tab.label}
          aria-current={isActive(tab.path) ? 'page' : undefined}
        >
          <tab.icon size={20} />
          <span className="bottom-tab-bar__label">{tab.label}</span>
        </button>
      ))}
    </nav>
  )
}
```

- [ ] **Step 3: Run build**

Run: `cd frontend && npm run build`
Expected: Build succeeds.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/components/layout/BottomTabBar.tsx frontend/src/components/layout/BottomTabBar.css
git commit -m "feat(ui): create BottomTabBar component for mobile navigation"
```

---

### Task 8: Rewire AppLayout to Use New Navigation

**Files:**
- Modify: `frontend/src/components/layout/AppLayout.tsx`
- Modify: `frontend/src/components/layout/AppLayout.css`

- [ ] **Step 1: Update AppLayout.tsx**

Replace with:

```tsx
import { Outlet } from 'react-router-dom'
import { IconRail } from './IconRail'
import { BottomTabBar } from './BottomTabBar'
import { ToastContainer } from '@/components/ui/toast'
import './AppLayout.css'

export function AppLayout() {
  return (
    <div className="app-layout">
      <aside className="sidebar-container">
        <IconRail />
      </aside>

      <div className="main-wrapper">
        <main className="main-content">
          <Outlet />
        </main>
        <BottomTabBar />
      </div>
      <ToastContainer />
    </div>
  )
}
```

- [ ] **Step 2: Update AppLayout.css**

Replace with:

```css
.app-layout {
  display: flex;
  height: 100dvh;
  overflow: hidden;
  background-color: var(--bg-primary);
  color: var(--text-primary);
}

.sidebar-container {
  display: none;
  flex-shrink: 0;
}

@media (min-width: 768px) {
  .sidebar-container {
    display: flex;
  }
}

.main-wrapper {
  display: flex;
  flex: 1;
  flex-direction: column;
  overflow: hidden;
}

.main-content {
  flex: 1;
  overflow-y: auto;
  padding: 1rem;
}

@media (min-width: 1024px) {
  .main-content {
    padding: 1.5rem;
  }
}
```

- [ ] **Step 3: Run build and verify**

Run: `cd frontend && npm run build`
Expected: Build succeeds. The old AppSidebar and MobileHeader are no longer imported but still exist (no breaking change).

- [ ] **Step 4: Commit**

```bash
git add frontend/src/components/layout/AppLayout.tsx frontend/src/components/layout/AppLayout.css
git commit -m "feat(ui): rewire AppLayout to IconRail (desktop) + BottomTabBar (mobile)"
```

---

## Phase 3: Screen Redesigns

> **Note for implementer:** Each screen task below involves restyling CSS and updating JSX structure. The data fetching, hooks, and business logic remain unchanged — only the presentation layer changes. Read the existing component code first, understand what data it uses, then restyle the markup and CSS.
>
> The tasks below provide the CSS changes and structural JSX patterns. For each screen, the implementer should:
> 1. Read the existing component to understand its data dependencies
> 2. Apply the new CSS tokens and layout patterns from the design spec
> 3. Keep all React Query hooks, Zustand stores, and event handlers as-is
> 4. Test visually in the browser at 320px, 768px, and 1024px+ widths

### Task 9: Redesign Dashboard Screen

**Files:**
- Modify: `frontend/src/pages/DashboardPage.tsx` (or equivalent)
- Modify: `frontend/src/components/dashboard/DashboardGrid.tsx`
- Modify: `frontend/src/components/dashboard/DashboardGrid.css`
- Modify: `frontend/src/components/dashboard/widgets/ConnectedAccountsWidget.tsx`
- Modify: `frontend/src/components/dashboard/widgets/ConnectedAccountsWidget.css`
- Modify: `frontend/src/components/dashboard/widgets/AccountSummaryWidget.tsx`
- Modify: `frontend/src/components/dashboard/widgets/AccountSummaryWidget.css`
- Modify: `frontend/src/components/dashboard/widgets/IrrWidget.tsx`
- Modify: `frontend/src/components/dashboard/widgets/IrrWidget.css`
- Modify: `frontend/src/components/dashboard/WidgetWrapper.css`

- [ ] **Step 1: Restyle ConnectedAccountsWidget**

Update to show accounts as horizontal strip with broker icon (Q/W/IB), account number (••XXXX), value in C$/US$, and gain/loss amount + percentage. Use the spec's accounts strip pattern from the dashboard design.

Key CSS changes:
- Horizontal flex layout with `overflow-x: auto` on desktop
- Each card: `background: var(--bg-secondary)`, `border: 1px solid var(--border)`, `border-radius: var(--radius-md)`
- "All Accounts" card gets emerald tint: `background: var(--accent-subtle)`, `border-color: var(--accent-border)`
- Broker icon: colored letter badge (Q green, W purple, IB red) with `border-radius: 5px`
- Account number in JetBrains Mono
- Values in JetBrains Mono with C$/US$ prefix

- [ ] **Step 2: Restyle AccountSummaryWidget as KPI row**

Replace the current 4-stat layout with the new 5-item row: Investment (dual currency breakdown), Cash (dual currency), Buying Power (dual currency), Returns (gain + ROI + IRR), Sectors (horizontal bars).

Key pattern for dual-currency cards:
- Primary value: large font, `font-family: var(--font-mono)`
- Divider: `height: 1px; background: var(--border)`
- Breakdown: two rows showing `C$` and `US$` amounts in muted text

Mobile: 2x2 grid instead of horizontal scroll.

- [ ] **Step 3: Restyle IrrWidget as Returns card**

Show total gain/loss in C$, ROI%, and IRR% using the divider + breakdown pattern.

- [ ] **Step 4: Update DashboardGrid layout**

Apply 4-5 column grid on desktop, 2-column on tablet, single column on mobile. Update `DashboardGrid.css` grid-template-columns.

- [ ] **Step 5: Update WidgetWrapper.css**

Update card styling: `background: var(--bg-secondary)`, `border: 1px solid var(--border)`, `border-radius: var(--radius-md)`, no box-shadow.

- [ ] **Step 6: Test in browser at 320px, 768px, 1024px**

Run: `cd frontend && npm run dev`
Verify dashboard renders correctly at all three breakpoints.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/components/dashboard/ frontend/src/pages/
git commit -m "feat(ui): redesign dashboard with Verdant Dark design system"
```

---

### Task 10: Redesign Broker Connections Screen

**Files:**
- Modify: `frontend/src/pages/BrokerConnectionsPage.tsx`
- Modify: `frontend/src/components/broker/BrokerCard.tsx`
- Modify: `frontend/src/components/broker/BrokerCard.css`
- Modify: `frontend/src/components/broker/BrokerConnectionCard.tsx`
- Modify: `frontend/src/components/broker/BrokerConnectionCard.css`

- [ ] **Step 1: Update BrokerConnectionsPage layout**

- Remove "Connect Broker" button from header
- Move Available Brokers section above Connected Accounts
- Available brokers are clickable — clicking opens ConnectBrokerDialog

- [ ] **Step 2: Restyle BrokerCard**

- Centered layout: broker icon (48px, colored), name, account types, connection state pill
- Connected: green dot + "N Account Connected"
- Needs reconnection: amber "Reconnect" pill
- Not connected: default state, clicking opens dialog
- Mobile: 3 equal-width columns using `flex: 1; min-width: 0`

- [ ] **Step 3: Restyle BrokerConnectionCard**

- Horizontal card: broker icon (40px) | account info (type, ••number, status dot, sync time) | value + gain/loss | Sync + More buttons
- Error state: `border-color: rgba(248,113,113,0.15)`, amber Reconnect button
- Status dots: green=active, amber=needs reconnection, red=error

- [ ] **Step 4: Test at all breakpoints**

- [ ] **Step 5: Commit**

```bash
git add frontend/src/pages/BrokerConnectionsPage.tsx frontend/src/components/broker/
git commit -m "feat(ui): redesign broker connections with Verdant Dark"
```

---

### Task 11: Redesign Account Detail Screen

**Files:**
- Modify: `frontend/src/pages/AccountDetailPage.tsx`
- Modify: `frontend/src/components/broker/CashBalanceCards.tsx`

- [ ] **Step 1: Update AccountDetailPage**

- Add breadcrumb navigation (Accounts > [AccountType])
- Account header: broker icon + type + ••number + status dot + sync time
- 4-column KPI row: Total Value (emerald tint), Investment (C$/US$), Cash (C$/US$), Returns (ROI/IRR)
- Tab bar: Positions / Activities / Dividends
- Mobile: 2x2 KPI grid, back arrow + breadcrumb

- [ ] **Step 2: Restyle CashBalanceCards**

Use dual-currency breakdown pattern (total + divider + C$/US$ rows).

- [ ] **Step 3: Test at all breakpoints**

- [ ] **Step 4: Commit**

```bash
git add frontend/src/pages/AccountDetailPage.tsx frontend/src/components/broker/CashBalanceCards.tsx
git commit -m "feat(ui): redesign account detail with breadcrumbs and dual-currency KPIs"
```

---

### Task 12: Redesign Options Trading Screen

**Files:**
- Modify: `frontend/src/pages/OptionsPage.tsx`
- Modify: `frontend/src/components/options/UnderlyingSearch.tsx`
- Modify: `frontend/src/components/options/UnderlyingSearch.css` (create if needed)
- Modify: `frontend/src/components/options/QuoteBar.tsx`
- Modify: `frontend/src/components/options/QuoteBar.css`
- Modify: `frontend/src/components/options/StrategySelector.tsx`
- Modify: `frontend/src/components/options/StrategySelector.css`
- Modify: `frontend/src/components/options/OptionsChainTable.tsx`
- Modify: `frontend/src/components/options/OptionsChainTable.css`
- Modify: `frontend/src/components/options/LegBuilder.tsx`
- Modify: `frontend/src/components/options/LegBuilder.css`
- Modify: `frontend/src/components/options/PnlChart.tsx`
- Modify: `frontend/src/components/options/PnlChart.css`

- [ ] **Step 1: Update OptionsPage layout**

Desktop: Two-column (55% chain / 45% sidebar). Search bar + quote bar + strategy pills at top.
Mobile: Single column with strategy dropdown + expiry dropdown (no horizontal scroll), Calls/Puts toggle, simplified 4-column chain, floating bottom bar.

- [ ] **Step 2: Restyle QuoteBar**

Desktop: Horizontal bar with symbol, price, bid, ask, spread, volume, change. Font sizes 15-22px.
Mobile: Compact two-line layout.

- [ ] **Step 3: Restyle StrategySelector**

Desktop: Pill buttons (13px, 7px 16px padding).
Mobile: Dropdown with chevron icon.

- [ ] **Step 4: Restyle OptionsChainTable**

Desktop: 7-column (Bid, Ask, Delta | Strike | Delta, Bid, Ask). ATM row: emerald left border + tinted background. Bid green, ask red. Data 14px JetBrains Mono.
Mobile: 4-column (Strike, Bid, Ask, Delta). Calls/Puts toggle shows one side at a time.

- [ ] **Step 5: Restyle LegBuilder**

Leg cards: 3-column grid (Strike, Mid, Expiry) with BUY/SELL + CALL/PUT badges. 12px badges, 15px values.

- [ ] **Step 6: Restyle PnlChart**

Desktop: In sidebar. 2x2 metrics (18px values), payoff SVG, breakeven row.
Mobile: Bottom sheet with drag handle. Shaded profit/loss zones on chart. Breakeven with amber marker.

- [ ] **Step 7: Test at all breakpoints**

- [ ] **Step 8: Commit**

```bash
git add frontend/src/pages/OptionsPage.tsx frontend/src/components/options/
git commit -m "feat(ui): redesign options trading with two-column layout and mobile bottom sheet"
```

---

### Task 13: Redesign Wheel Strategy Screen

**Files:**
- Modify: `frontend/src/pages/WheelPage.tsx`
- Modify: `frontend/src/components/wheel/WheelGrid.tsx`
- Modify: `frontend/src/components/wheel/WheelGrid.css`
- Modify: `frontend/src/components/wheel/PositionCard.tsx`
- Modify: `frontend/src/components/wheel/PositionCard.css`
- Modify: `frontend/src/components/wheel/CapitalSummary.tsx`
- Modify: `frontend/src/components/wheel/CapitalSummary.css`
- Modify: `frontend/src/components/wheel/ClosePositionDialog.tsx`
- Modify: `frontend/src/components/wheel/ClosePositionDialog.css`

- [ ] **Step 1: Update WheelPage layout**

- Legend in header (desktop: full labels, mobile: abbreviated CSP/CC/Open)
- Desktop: Account tab pills. Mobile: Account dropdown.
- Header "+" button on mobile (40px emerald, top-right)

- [ ] **Step 2: Restyle CapitalSummary**

Desktop: Horizontal bar with dividers (Available Cash with C$/US$ breakdown, CSP Deployed, CCs Written, Premium, Unrealized P&L).
Mobile: 2x2 grid.

- [ ] **Step 3: Restyle PositionCard**

- Remove CSP/CC text badges — type indicated by color only
- CSP: `background: var(--csp-bg)`, `border: 1.5px solid var(--csp-border)` (indigo)
- CC: `background: var(--cc-bg)`, `border: 1.5px solid var(--cc-border)` (orange)
- Content: Strike price + OTM% on first row, premium + P&L on second
- Desktop: Add emerald "+" button in top-right corner (20px circle)

- [ ] **Step 4: Restyle WheelGrid**

Desktop: Expiry rows x Ticker columns grid. DTE badges (red/yellow/green). Totals row.
Mobile: Positions grouped by expiry date as collapsible sections with position cards listed vertically.

- [ ] **Step 5: Restyle ClosePositionDialog**

Update to use new tokens: `--bg-secondary` background, `--border` borders, `--radius-lg` border-radius.

- [ ] **Step 6: Test at all breakpoints**

- [ ] **Step 7: Commit**

```bash
git add frontend/src/pages/WheelPage.tsx frontend/src/components/wheel/
git commit -m "feat(ui): redesign wheel strategy with indigo CSP / orange CC colors, mobile expiry groups"
```

---

### Task 14: Redesign Admin Panel Screen

**Files:**
- Modify: `frontend/src/pages/admin/AdminPage.tsx`
- Create: `frontend/src/pages/admin/AdminPage.css`

- [ ] **Step 1: Create AdminPage.css**

Style the admin panel sections:
- Summary stats: 6-column grid on desktop, 2x2 on mobile
- Instrument types: 6-column grid inside a card
- Workflows + Recent Runs: 2-column on desktop, stacked on mobile
- Auto-refresh indicator: green dot + label in header
- Progress bars: `background: var(--border)` track, `var(--accent)` fill

- [ ] **Step 2: Update AdminPage.tsx markup**

Apply new class names and layout structure. Keep all React Query hooks and mutation logic unchanged.

- [ ] **Step 3: Test at all breakpoints**

- [ ] **Step 4: Commit**

```bash
git add frontend/src/pages/admin/
git commit -m "feat(ui): redesign admin panel with Verdant Dark tokens"
```

---

## Phase 4: Cleanup and Verification

### Task 15: Remove Old Navigation Components

**Files:**
- Modify: `frontend/src/components/layout/AppSidebar.tsx` (deprecate or remove)
- Modify: `frontend/src/components/layout/MobileHeader.tsx` (deprecate or remove)
- Remove unused CSS files if no longer imported

- [ ] **Step 1: Check for remaining imports of AppSidebar and MobileHeader**

Run: `grep -r "AppSidebar\|MobileHeader" frontend/src/ --include="*.tsx" --include="*.ts"`

If only imported in old AppLayout (which was already updated), safe to remove.

- [ ] **Step 2: Remove or archive the files**

- [ ] **Step 3: Run build and tests**

Run: `cd frontend && npm run build && npm run test:run && npm run lint`
Expected: All pass.

- [ ] **Step 4: Commit**

```bash
git add -A frontend/src/components/layout/
git commit -m "chore(ui): remove deprecated AppSidebar and MobileHeader"
```

---

### Task 16: Full Visual Verification

- [ ] **Step 1: Start dev server**

Run: `cd frontend && npm run dev`

- [ ] **Step 2: Test all screens at mobile (320px)**

Verify in browser devtools:
- Dashboard: 2x2 KPI grid, stacked accounts, bottom tab bar
- Connections: 3-column equal-width broker grid, stacked accounts
- Account Detail: 2x2 KPIs, back arrow
- Options: Strategy + Expiry dropdowns, Calls/Puts toggle, 4-column chain
- Wheel: Account dropdown, 2x2 capital summary, expiry-grouped positions
- Admin: 2x2 stats, stacked workflows

- [ ] **Step 3: Test all screens at tablet (768px)**

Verify: Icon rail visible, flyout on tap, 2-column grids.

- [ ] **Step 4: Test all screens at desktop (1440px)**

Verify: Icon rail + flyout, 4-5 column grids, two-column layouts.

- [ ] **Step 5: Toggle light/dark mode on each screen**

Verify all tokens apply correctly in both themes.

- [ ] **Step 6: Run full test suite**

Run: `cd frontend && npm run build && npm run test:run && npm run lint`
Expected: All pass.

- [ ] **Step 7: Final commit**

```bash
git add -A
git commit -m "feat(ui): complete Verdant Dark redesign — all screens verified"
```

---

### Task 17: Update Documentation

**Files:**
- Modify: `docs/reference/frontend-map.md`
- Modify: `docs/business-context.html` (if architecture changed)

- [ ] **Step 1: Update frontend-map.md**

Add new components: `IconRail`, `BottomTabBar`. Note removal of `AppSidebar`, `MobileHeader`. Document the new navigation pattern.

- [ ] **Step 2: Archive the design spec**

```bash
git mv docs/superpowers/specs/2026-05-25-ui-redesign-design.md .archive/
git mv docs/superpowers/plans/2026-05-25-ui-redesign.md .archive/
```

- [ ] **Step 3: Commit**

```bash
git add docs/ .archive/
git commit -m "docs: update frontend-map for Verdant Dark redesign, archive spec and plan"
```
