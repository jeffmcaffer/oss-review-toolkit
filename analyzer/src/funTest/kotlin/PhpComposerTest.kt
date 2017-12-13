/*
 * Copyright (c) 2017 HERE Europe B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 * License-Filename: LICENSE
 */

package com.here.ort.analyzer

import com.here.ort.analyzer.managers.PhpComposer

import io.kotlintest.matchers.shouldBe
import io.kotlintest.matchers.shouldNotBe
import io.kotlintest.specs.StringSpec

import java.io.File

private val projectDir = File("src/funTest/assets/projects/synthetic/php-composer")

class PhpComposerTest : StringSpec() {
    init {
        "Php composer recognises project" {
            val result = PackageManager.findManagedFiles(projectDir, listOf(PhpComposer))
            result[PhpComposer]?.isEmpty() shouldBe false
        }

        "Project dependencies are detected correctly" {
            val packageFile = File(projectDir, "composer.json")
//            val processCapt = ProcessCapture(projectDir, PhpComposer.create().command(projectDir), "install").stderr()
//            println(processCapt)
            val result = PhpComposer.create().resolveDependencies(projectDir, listOf(packageFile))[packageFile]
            result shouldNotBe null
        }
    }
}
