import unittest

from effects import MODES
from psysualizer import RECIPES


class RecipeTests(unittest.TestCase):
    def test_recipes_reference_registered_modes_and_valid_alpha(self):
        names = {name for name, _ in MODES}
        self.assertEqual(len(RECIPES), 5)
        for recipe in RECIPES:
            self.assertIn(recipe["foreground"], names)
            self.assertIn(recipe["background"], names)
            self.assertNotEqual(recipe["foreground"], recipe["background"])
            self.assertGreaterEqual(recipe["bg_alpha"], 0)
            self.assertLessEqual(recipe["bg_alpha"], 255)


if __name__ == "__main__":
    unittest.main()
