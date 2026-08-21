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
from .morphogenesis import Morphogenesis
from .hyperbolic import Hyperbolic
from .liquidlight import LiquidLight
from .cymatica import Cymatica
from .phason import Phason
from .tesseract import Tesseract
from .ferrofluid import Ferrofluid
from .mandelbox import Mandelbox
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
    ("Morphogenesis", Morphogenesis), # ←/→ only
    ("Hyperbolic",   Hyperbolic),     # ←/→ only
    ("LiquidLight",  LiquidLight),    # ←/→ only
    ("Cymatica",     Cymatica),       # ←/→ only
    ("Phason",       Phason),         # ←/→ only
    ("Tesseract",    Tesseract),      # ←/→ only
    ("Ferrofluid",   Ferrofluid),     # ←/→ only
    ("Mandelbox",    Mandelbox),      # ←/→ only
    # ── Spectrum and Waterfall must always be the final two entries ──
    ("Spectrum",    Bars),        # ←/→ only — always second-to-last
    ("Waterfall",   GlowSquares), # ←/→ only — always last
]
