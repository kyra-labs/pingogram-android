import json
import os
import unittest

import check_shards


class CheckShardsTest(unittest.TestCase):
    def test_reads_the_included_projects_and_the_root(self):
        text = 'include(":app")\ninclude(":core:ui")\n// include(":old")\nincludeBuild("build-logic")\n'
        self.assertEqual({":", ":app", ":core:ui"}, check_shards.included_projects(text))

    def test_turns_groups_into_check_tasks(self):
        shards = [{"name": "a", "projects": [":", ":app"]}, {"name": "b", "projects": [":core:ui"]}]
        result = check_shards.matrix(shards, {":", ":app", ":core:ui"})
        self.assertEqual(
            [{"name": "a", "tasks": ":check :app:check"}, {"name": "b", "tasks": ":core:ui:check"}], result
        )

    def test_fails_on_a_project_in_no_group(self):
        with self.assertRaisesRegex(ValueError, ":core:new is in no group"):
            check_shards.matrix([{"name": "a", "projects": [":"]}], {":", ":core:new"})

    def test_fails_on_a_project_in_two_groups(self):
        shards = [{"name": "a", "projects": [":", ":app"]}, {"name": "b", "projects": [":app"]}]
        with self.assertRaisesRegex(ValueError, ":app is in both a and b"):
            check_shards.matrix(shards, {":", ":app"})

    def test_fails_on_a_project_settings_does_not_include(self):
        with self.assertRaisesRegex(ValueError, ":gone is not a project"):
            check_shards.matrix([{"name": "a", "projects": [":", ":gone"]}], {":"})

    def test_the_repository_groups_cover_every_project(self):
        with open(os.path.join(check_shards.HERE, "check-shards.json"), encoding="utf-8") as f:
            shards = json.load(f)["shards"]
        with open(os.path.join(check_shards.ROOT, "settings.gradle.kts"), encoding="utf-8") as f:
            projects = check_shards.included_projects(f.read())
        check_shards.matrix(shards, projects)


if __name__ == "__main__":
    unittest.main()
