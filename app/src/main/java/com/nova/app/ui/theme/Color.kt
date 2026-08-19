package com.nova.app.ui.theme

import androidx.compose.ui.graphics.Color

// Base surfaces — near-black, slightly blue rather than pure neutral,
// so it reads as "instrumentation" rather than generic "dark mode."
val NovaBackground = Color(0xFF0A0D12)
val NovaSurface = Color(0xFF11151C)
val NovaSurfaceRaised = Color(0xFF161B24)
val NovaSurfaceOutline = Color(0xFF232A36)

// Single accent, used sparingly (status, active states, key numerals).
// A restrained signal-green rather than neon — instrument-panel, not gamer RGB.
val NovaAccent = Color(0xFF3DDC97)
val NovaAccentDim = Color(0xFF2A9C6B)

// Status colors — used only for genuine states, never decoration.
val NovaGood = Color(0xFF3DDC97)
val NovaWarn = Color(0xFFE8B24C)
val NovaBad = Color(0xFFE05252)
val NovaNeutral = Color(0xFF7A8699)

// Text
val NovaTextPrimary = Color(0xFFEDEFF3)
val NovaTextSecondary = Color(0xFF9AA4B2)
val NovaTextTertiary = Color(0xFF616B7A)

// Light-mode fallback tokens (device forced to light mode). Kept minimal —
// NOVA is dark-first per spec, this just prevents a broken UI, not a redesign.
val Color_White = Color(0xFFF5F6F8)
val Color_Ink = Color(0xFF11151C)
val Color_LightSurface = Color(0xFFE7E9ED)
val Color_LightOutline = Color(0xFFD3D7DE)
