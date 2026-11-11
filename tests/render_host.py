"""Per-test host: explicitly constructs effects and advances their contexts.

This fixture has no module-level state. It exercises the production adapters;
tests of render isolation also construct effects directly, without this helper.
"""
from dataclasses import replace, fields
from core.context import AudioConfig, EffectOptions, FrameContext
from effects.palette import ColorPalette
from weakref import WeakSet


class RenderHost:
    def __init__(self, width=320, height=240):
        object.__setattr__(self, '_frame', FrameContext(width, height))
        object.__setattr__(self, '_effects', WeakSet())
        object.__setattr__(self, '_apps', WeakSet())
        self.audio = AudioConfig()
        self.low_spec = False
        self.seed = 0
        self.initialized = True
        self.default_effect_gain = .7

    def __getattr__(self, name):
        return getattr(self._frame, name)

    def __setattr__(self, name, value):
        if name in {f.name for f in fields(FrameContext)}:
            object.__setattr__(self, '_frame', replace(self._frame, **{name: value}))
            for effect in self._effects:
                effect.set_frame_context(self._frame)
            for app in self._apps:
                app.frame = self._frame
                app.fps = self._frame.fps
                app.visual_profile = self._frame.profile
        else:
            object.__setattr__(self, name, value)

    def create(self, cls, *args, **kwargs):
        effect = cls(*args, frame=self._frame, options=EffectOptions(self.width, self.height,
                     low_spec=self.low_spec, seed=self.seed, audio=self.audio), **kwargs)
        effect.set_frame_context(self._frame)
        self._effects.add(effect)
        return effect

    def bare(self, cls):
        from effects.base import Effect
        effect = object.__new__(cls)
        Effect.__init__(effect, options=EffectOptions(self.width, self.height,
                        low_spec=self.low_spec, seed=self.seed, audio=self.audio))
        effect.set_frame_context(self._frame)
        self._effects.add(effect)
        return effect

    def app(self):
        from psysualizer import VisualizerApp
        app = VisualizerApp.__new__(VisualizerApp)
        app.frame, app.fps = self._frame, self.fps
        app.visual_profile, app.seed = self.profile, self.seed
        app.audio_config, app.palette = self.audio, ColorPalette()
        self._apps.add(app)
        return app
