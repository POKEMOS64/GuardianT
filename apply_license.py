import os

LICENSE_HEADER = """/*
 * Copyright (C) 2026 GuardianT Project
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

"""

def add_header_to_kotlin_files(root_dir):
    print(f"Scanning directory: {root_dir}")
    for root, dirs, files in os.walk(root_dir):
        for file in files:
            if file.endswith(".kt"):
                file_path = os.path.join(root, file)
                with open(file_path, "r", encoding="utf-8") as f:
                    content = f.read()
                
                if "GNU Affero General Public License" not in content:
                    with open(file_path, "w", encoding="utf-8") as f:
                        f.write(LICENSE_HEADER + content)
                    print(f"Added license to: {file}")
                else:
                    print(f"Skipped (already exists): {file}")

if __name__ == "__main__":
    # Применяем к папке android
    add_header_to_kotlin_files("android")