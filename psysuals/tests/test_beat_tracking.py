import unittest
from unittest import mock

import beat_tracking


class BeatTrackerLifecycleTests(unittest.TestCase):
    def test_release_is_idempotent_and_analyze_keeps_fallback(self):
        tracker = beat_tracking.LibrosaBeatTracker(44100, 1024)
        try:
            tracker.release()
            self.assertEqual(tracker.analyze(123.0), 123.0)
            tracker.release()
        finally:
            tracker.release()

    def test_submission_failure_clears_running_flag(self):
        tracker = beat_tracking.LibrosaBeatTracker(44100, 1024)
        try:
            with mock.patch.object(beat_tracking, "_get_librosa", return_value=object()), \
                 mock.patch.object(tracker._executor, "submit", side_effect=RuntimeError("released")):
                tracker._blocks.appendleft(b"block")
                tracker._last_analysis = -1e9
                tracker.analyze()
            self.assertFalse(tracker._analysis_running)
        finally:
            tracker.release()

    def test_failed_analysis_clears_running_flag(self):
        tracker = beat_tracking.LibrosaBeatTracker(44100, 1024)
        try:
            with mock.patch.object(tracker, "_analyze_blocks", side_effect=RuntimeError("bad analysis")):
                tracker._run_analysis((), 0.0, 0.0)
            self.assertFalse(tracker._analysis_running)
        finally:
            tracker.release()


if __name__ == "__main__":
    unittest.main()
