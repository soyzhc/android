// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.android.tools.idea.projectsystem

import com.android.ide.common.gradle.model.IdeAndroidProject
import com.android.ide.common.gradle.model.IdeVariant
import com.android.projectmodel.AndroidPathType
import com.android.projectmodel.AndroidProject
import com.android.projectmodel.PathString
import com.android.projectmodel.matchArtifactsWith
import com.android.tools.idea.projectsystem.gradle.MAIN_ARTIFACT_NAME
import com.android.tools.idea.projectsystem.gradle.filesToPathStrings
import com.android.tools.idea.projectsystem.gradle.getProjectType
import com.android.tools.idea.projectsystem.gradle.toProjectModel
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.TestProjectPaths
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Tests for [GradleModelConverter]. The setup time for these tests are quite slow since it needs to perform a Gradle sync.
 * In order to avoid doing this multiple times, this whole suite is written as a single "test" that invokes multiple "check"
 * methods. Each "check" method validates a bunch of related invariants on the model.
 */
class GradleModelConverterTest : AndroidGradleTestCase() {
  lateinit var project : IdeAndroidProject
  lateinit var converted: AndroidProject

  override fun setUp() {
    super.setUp()
    loadProject(TestProjectPaths.PROJECTMODEL_MULTIFLAVOR)
    project = model.androidProject
    converted = project.toProjectModel()
  }

  fun testConversion() {
    checkProjectAttributes()
//    TODO: The following disabled tests fail when run from the IDE, but work when run via Bazel
//    checkBuildTypeConfigurations()
//    checkFlavorConfigurations()
    checkVariants()
  }

  fun checkProjectAttributes() {
    assertThat(converted.name).isEqualTo(project.name)
    assertThat(converted.type).isEqualTo(getProjectType(project.projectType))
  }

//  TODO: This fails from the IDE, but works from Bazel
//  fun checkBuildTypeConfigurations() {
//    with(converted.configTable) {
//      val debugPath = schema.pathFor("debug")
//      val debugConfigs = filter { debugPath.contains(it.path) }.configs
//
//      assertThat(debugConfigs.size).isEqualTo(15)
//
//      with(debugConfigs[0]) {
//        with(manifestValues) {
//          assertThat(debuggable).isTrue()
//          assertThat(compileSdkVersion).isNull()
//          assertThat(applicationId).isNull()
//        }
//        assertThat(sources.get(AndroidPathType.ASSETS)[0].portablePath.endsWith("src/debug/assets")).isTrue()
//      }
//    }
//  }

//  TODO: Fails with: Not true that <17> is equal to <19> at assertThat(x86Configs.size).isEqualTo(19), when run from the IDE
//  fun checkFlavorConfigurations() {
//    with(converted.configTable) {
//      val x86Path = schema.pathFor("x86")
//      val x86Configs = filter { x86Path.contains(it.path) }.configs
//
//      assertThat(x86Configs.size).isEqualTo(19)
//
//      val mainArtifactPath = x86Path.intersect(schema.pathFor(MAIN_ARTIFACT_NAME))
//      val mainArtifactX86Configs = filter { mainArtifactPath.contains(it.path) }.configs
//
//      assertThat(mainArtifactX86Configs.size).isEqualTo(7)
//
//      with(mainArtifactX86Configs[0]) {
//        with(manifestValues) {
//          assertThat(debuggable).isNull()
//          assertThat(compileSdkVersion).isNull()
//          assertThat(applicationId).isNull()
//        }
//        assertThat(sources.get(AndroidPathType.MANIFEST)[0].portablePath.endsWith("src/x86/AndroidManifest.xml")).isTrue()
//      }
//    }
//  }

  fun checkVariants() {
    with(converted) {
      assertThat(variants.size).isEqualTo(8)
      val originalVariant = firstVariant()
      val originalTestArtifact = originalVariant.extraAndroidArtifacts.iterator().next()!!
      val originalArtifact = originalVariant.mainArtifact
      val variant = variants[0]
      val mainArtifact = variant.mainArtifact
      val testArtifact = variant.androidTestArtifact

      with (mainArtifact) {
        assertThat(name).isEqualTo(MAIN_ARTIFACT_NAME)
        assertThat(classFolders).isEqualTo(
            listOf(PathString(originalArtifact.classesFolder)) + filesToPathStrings(originalArtifact.additionalClassesFolders))
        assertThat(packageName).isEqualTo(project.defaultConfig.productFlavor.applicationId)
        with(resolved) {
          assertThat(usingSupportLibVectors).isFalse()
          with(manifestValues) {
            assertThat(applicationId).isEqualTo(originalArtifact.applicationId)
          }
          assertThat(sources[AndroidPathType.MANIFEST].size).isEqualTo(6)
        }
      }

//      TODO: Fails with Not true that <4> is equal to <6> at assertThat(sources[AndroidPathType.MANIFEST].size).isEqualTo(6)
//      with (testArtifact!!) {
//        assertThat(name).isEqualTo(originalTestArtifact.name)
//        assertThat(classFolders).isEqualTo(
//            listOf(PathString(originalTestArtifact.classesFolder)) + filesToPathStrings(originalTestArtifact.additionalClassesFolders))
//        // Note that we don't currently fill in the package name for test artifacts, since the gradle model doesn't return it
//        with(resolved) {
//          assertThat(usingSupportLibVectors).isFalse()
//          with(manifestValues) {
//            assertThat(applicationId).isEqualTo(originalTestArtifact.applicationId)
//          }
//          assertThat(sources[AndroidPathType.MANIFEST].size).isEqualTo(6)
//        }
//      }

      assertThat(variant.mainArtifactConfigPath).isEqualTo(matchArtifactsWith("free/x86/debug/main"))
      assertThat(variant.configPath).isEqualTo(matchArtifactsWith("free/x86/debug"))
    }
  }

  private fun firstVariant(): IdeVariant {
    var originalVariantVar: IdeVariant? = null
    project.forEachVariant {
      if (originalVariantVar == null) {
        originalVariantVar = it
      }
    }
    val originalVariant = originalVariantVar!!
    return originalVariant
  }
}