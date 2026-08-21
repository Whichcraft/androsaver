import types
import unittest
import sys
from unittest import mock

import numpy as np

try:
    import core.audio_engine as audio_module
    AUDIO_IMPORT_ERROR = None
except Exception as exc:  # pragma: no cover - dependency-specific environment
    if isinstance(exc, ModuleNotFoundError) and exc.name == "sounddevice":
        sys.modules["sounddevice"] = types.SimpleNamespace(InputStream=None)
        try:
            import core.audio_engine as audio_module
            AUDIO_IMPORT_ERROR = None
        except Exception as retry_exc:
            audio_module = None
            AUDIO_IMPORT_ERROR = retry_exc
    else:
        audio_module = None
        AUDIO_IMPORT_ERROR = exc


@unittest.skipIf(audio_module is None, f"audio dependencies unavailable: {AUDIO_IMPORT_ERROR}")
class AudioEngineTests(unittest.TestCase):
    def setUp(self):
        self.engine = audio_module.AudioEngine()
        self.pushed = []

        class Tracker:
            def push_audio(inner, block, end_time):
                self.pushed.append((block.copy(), end_time))

            def release(inner):
                self.released = True

        self.engine.beat_tracker = Tracker()
        self.released = False

    def tearDown(self):
        self.engine.stop_input_stream()

    def test_genre_warmup_and_reset(self):
        self.assertIsNone(self.engine.detect_genre())
        self.engine._detect_accum[:] = 1.0
        self.engine._detect_frames = self.engine._DETECT_MIN
        self.assertEqual(self.engine.detect_genre(), "any")
        self.assertEqual(self.engine._detect_frames, 0)
        self.assertTrue(np.all(self.engine._detect_accum == 0))

    def test_audio_callback_preserves_short_capture_and_records_time(self):
        time_info = types.SimpleNamespace(currentTime=12.5)
        block = np.ones((128, 1), dtype=np.float32)

        self.engine._audio_cb(block, 128, time_info, None)

        self.assertEqual(len(self.pushed), 1)
        self.assertEqual(self.pushed[0][0].shape, (128,))
        self.assertEqual(self.pushed[0][1], 12.5)
        self.assertEqual(self.engine.get_audio()[-1], 12.5)

    def test_audio_callback_uses_newest_samples_for_long_blocks(self):
        time_info = types.SimpleNamespace(currentTime=3.0)
        block = np.arange(audio_module.config.BLOCK_SIZE + 100, dtype=np.float32)[:, None]

        self.engine._audio_cb(block, block.shape[0], time_info, None)

        self.assertEqual(self.pushed[0][0].shape, (audio_module.config.BLOCK_SIZE,))
        np.testing.assert_array_equal(self.pushed[0][0], block[-audio_module.config.BLOCK_SIZE:, 0])

    def test_audio_callback_uses_adc_end_time_when_available(self):
        time_info = types.SimpleNamespace(currentTime=20.0, inputBufferAdcTime=12.0)
        block = np.zeros((audio_module.config.BLOCK_SIZE, 1), dtype=np.float32)

        self.engine._audio_cb(block, block.shape[0], time_info, None)

        expected = 12.0 + audio_module.config.BLOCK_SIZE / audio_module.config.SAMPLE_RATE
        self.assertAlmostEqual(self.pushed[0][1], expected)
        self.assertAlmostEqual(self.engine.get_audio()[-1], expected)

    def test_identical_consecutive_blocks_have_no_flux(self):
        time_info = types.SimpleNamespace(currentTime=1.0)
        silence = np.zeros((audio_module.config.BLOCK_SIZE, 1), dtype=np.float32)
        tone = np.ones((audio_module.config.BLOCK_SIZE, 1), dtype=np.float32)

        self.engine._audio_cb(silence, silence.shape[0], time_info, None)
        time_info.currentTime = 2.0
        self.engine._audio_cb(tone, tone.shape[0], time_info, None)
        time_info.currentTime = 3.0
        self.engine._audio_cb(tone, tone.shape[0], time_info, None)

        self.assertLess(self.engine.get_audio()[2], 1e-5)

    def test_genre_weight_change_resets_flux_baseline(self):
        time_info = types.SimpleNamespace(currentTime=1.0)
        tone = np.ones((audio_module.config.BLOCK_SIZE, 1), dtype=np.float32)

        self.engine._audio_cb(tone, tone.shape[0], time_info, None)
        self.engine.apply_genre_weights("electronic")
        time_info.currentTime = 2.0
        self.engine._audio_cb(tone, tone.shape[0], time_info, None)

        self.assertLess(self.engine.get_audio()[2], 1e-5)

    def test_reapplying_same_genre_does_not_reset_flux_baseline(self):
        self.engine.apply_genre_weights("rock")
        self.engine._first_frame = False
        self.engine.apply_genre_weights("rock")
        self.assertFalse(self.engine._first_frame)

    def test_open_input_stream_tries_candidates_until_success(self):
        streams = []

        class FakeStream:
            active = True

            def start(inner):
                streams.append(inner)

            def stop(inner):
                pass

            def close(inner):
                pass

        def make_stream(**kwargs):
            if kwargs["device"] == 1:
                raise OSError("device unavailable")
            return FakeStream()

        fake_sd = types.SimpleNamespace(InputStream=mock.Mock(side_effect=make_stream))
        with mock.patch.object(audio_module, "sd", fake_sd):
            stream, device = self.engine.open_input_stream(1, 2)

        self.assertIsNotNone(stream)
        self.assertEqual(device, 2)
        self.assertEqual(self.engine.active_dev, 2)

    def test_failed_device_switch_keeps_existing_stream(self):
        class ExistingStream:
            active = True
            def stop(inner):
                inner.stopped = True
            def close(inner):
                inner.closed = True

        existing = ExistingStream()
        self.engine.stream = existing
        self.engine.active_dev = 7

        def fail_stream(**kwargs):
            raise OSError("device unavailable")

        fake_sd = types.SimpleNamespace(InputStream=mock.Mock(side_effect=fail_stream))
        with mock.patch.object(audio_module, "sd", fake_sd):
            self.assertIsNone(self.engine.start_input_stream(9))

        self.assertIs(self.engine.stream, existing)
        self.assertEqual(self.engine.active_dev, 7)
        self.assertFalse(hasattr(existing, "stopped"))

    def test_release_without_stream_is_safe(self):
        self.engine.release()
        self.assertTrue(self.released)

    def test_stream_teardown_failure_does_not_escape(self):
        class BrokenStream:
            active = True

            def stop(inner):
                raise OSError("stop failed")

            def close(inner):
                raise OSError("close failed")

        self.engine.stream = BrokenStream()
        self.engine.active_dev = 7
        self.engine.stop_input_stream()

        self.assertIsNone(self.engine.stream)
        self.assertIsNone(self.engine.active_dev)
        self.assertIsInstance(self.engine.last_error, OSError)


if __name__ == "__main__":
    unittest.main()
