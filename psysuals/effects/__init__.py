from .yantra      import Yantra
from .cube        import Cube
from .plasma_gl   import PlasmaGL as Plasma
from .tunnel      import Tunnel
from .lissajous   import Lissajous
from .corridor    import Corridor
from .nova        import Nova
from .spiral      import Spiral
from .bubbles     import Bubbles
from .triflux     import Attractor
from .branches    import Branches
from .butterflies        import Butterflies
from .flowfield          import FlowField
from .fireworks        import Fireworks
from .aurora             import Aurora
from .lattice            import Lattice
from .mycelium    import Mycelium
from .magnetar    import Magnetar
from .slimemold   import SlimeMold
from .mobius      import Mobius
from .chromatic   import Chromatic
from .persistence import Persistence
from .synapse     import Synapse
from .heartbeat   import Heartbeat
from .hyperbolic import Hyperbolic
from .liquidlight import LiquidLight
from .cymatica import Cymatica
from .phason import Phason
from .tesseract import Tesseract
from .ferrofluid import Ferrofluid
from .chromaweave import ChromaWeave
from .horizon import Horizon
from .caustics import Caustics
from .lanterns import Lanterns
from .spectrum           import Bars
from .waterfall   import GlowSquares

MODES = [
    ("Yantra",      Yantra),      # 1
    ("Cube",        Cube),        # 2
    ("TriFlux",     Attractor),   # 3
    ("Lissajous",   Lissajous),   # 4
    ("Tunnel",      Tunnel),      # 5
    ("Corridor",    Corridor),    # 6
    ("Nova",        Nova),        # 7
    ("Spiral",      Spiral),      # 8
    ("Bubbles",     Bubbles),     # 9
    ("Plasma",      Plasma),      # ←/→ only
    ("Branches",    Branches),    # ←/→ only
    ("Butterflies", Butterflies), # ←/→ only
    ("FlowField",   FlowField),   # ←/→ only
    ("Fireworks",   Fireworks),   # ←/→ only
    ("Aurora",      Aurora),      # ←/→ only
    ("Lattice",     Lattice),     # ←/→ only
    ("Mycelium",    Mycelium),    # ←/→ only
    ("Magnetar",    Magnetar),    # ←/→ only
    ("SlimeMold",   SlimeMold),   # ←/→ only
    ("Mobius",      Mobius),      # ←/→ only
    ("Chromatic",   Chromatic),   # ←/→ only
    ("Persistence", Persistence), # ←/→ only
    ("Synapse",     Synapse),     # ←/→ only
    ("Heartbeat",   Heartbeat),   # ←/→ only
    ("Hyperbolic",   Hyperbolic),     # ←/→ only
    ("LiquidLight",  LiquidLight),    # ←/→ only
    ("Cymatica",     Cymatica),       # ←/→ only
    ("Phason",       Phason),         # ←/→ only
    ("Tesseract",    Tesseract),      # ←/→ only
    ("Ferrofluid",   Ferrofluid),     # ←/→ only
    ("ChromaWeave",  ChromaWeave),    # ←/→ only
    ("Horizon",      Horizon),        # ←/→ only
    ("Caustics",     Caustics),       # ←/→ only
    ("Lanterns",     Lanterns),       # ←/→ only
    # ── Spectrum and Waterfall must always be the final two entries ──
    ("Spectrum",    Bars),        # ←/→ only — always second-to-last
    ("Waterfall",   GlowSquares), # ←/→ only — always last
]


MODE_NAMES = [name for name, _ in MODES]
# Registry order up to 3.22.0, before settings stored mode names. Files saved
# without a name hold an index into this list.
_NAMELESS_ORDER = [name for name in MODE_NAMES
                   if name not in ("Horizon", "Caustics", "Lanterns")]


def resolve_mode(index, name=None):
    """Registry index for a saved mode, preferring its saved name.

    Settings and presets store the mode name next to its index, so registry
    insertions no longer move the user to a different effect. Data saved
    without a name predates them and is mapped through the 3.22 order.
    """
    if isinstance(name, str):
        if name in MODE_NAMES:
            return MODE_NAMES.index(name)
        return index
    if isinstance(index, int) and 0 <= index < len(_NAMELESS_ORDER):
        return MODE_NAMES.index(_NAMELESS_ORDER[index])
    return index
