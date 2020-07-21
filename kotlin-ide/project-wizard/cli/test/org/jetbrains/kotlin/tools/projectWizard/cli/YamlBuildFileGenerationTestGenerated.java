/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.tools.projectWizard.cli;

import com.intellij.testFramework.TestDataPath;
import org.jetbrains.kotlin.test.JUnit3RunnerWithInners;
import org.jetbrains.kotlin.test.KotlinTestUtils;
import org.jetbrains.kotlin.test.TestMetadata;
import org.jetbrains.kotlin.test.TestRoot;
import org.junit.runner.RunWith;

/*
 * This class is generated by {@link org.jetbrains.kotlin.generators.tests.TestsPackage}.
 * DO NOT MODIFY MANUALLY.
 */
@SuppressWarnings("all")
@TestRoot("project-wizard/cli")
@TestDataPath("$CONTENT_ROOT")
@RunWith(JUnit3RunnerWithInners.class)
@TestMetadata("testData/buildFileGeneration")
public class YamlBuildFileGenerationTestGenerated extends AbstractYamlBuildFileGenerationTest {
    private void runTest(String testDataFilePath) throws Exception {
        KotlinTestUtils.runTest(this::doTest, this, testDataFilePath);
    }

    @TestMetadata("android")
    public void testAndroid() throws Exception {
        runTest("testData/buildFileGeneration/android/");
    }

    @TestMetadata("jsNodeAndBrowserTargets")
    public void testJsNodeAndBrowserTargets() throws Exception {
        runTest("testData/buildFileGeneration/jsNodeAndBrowserTargets/");
    }

    @TestMetadata("jvmTarget")
    public void testJvmTarget() throws Exception {
        runTest("testData/buildFileGeneration/jvmTarget/");
    }

    @TestMetadata("jvmTargetWithJava")
    public void testJvmTargetWithJava() throws Exception {
        runTest("testData/buildFileGeneration/jvmTargetWithJava/");
    }

    @TestMetadata("jvmToJvmDependency")
    public void testJvmToJvmDependency() throws Exception {
        runTest("testData/buildFileGeneration/jvmToJvmDependency/");
    }

    @TestMetadata("jvmToJvmDependencyWithSingleRoot")
    public void testJvmToJvmDependencyWithSingleRoot() throws Exception {
        runTest("testData/buildFileGeneration/jvmToJvmDependencyWithSingleRoot/");
    }

    @TestMetadata("kotlinJvm")
    public void testKotlinJvm() throws Exception {
        runTest("testData/buildFileGeneration/kotlinJvm/");
    }

    @TestMetadata("nativeForCurrentSystem")
    public void testNativeForCurrentSystem() throws Exception {
        runTest("testData/buildFileGeneration/nativeForCurrentSystem/");
    }

    @TestMetadata("simpleMultiplatform")
    public void testSimpleMultiplatform() throws Exception {
        runTest("testData/buildFileGeneration/simpleMultiplatform/");
    }

    @TestMetadata("simpleNativeTarget")
    public void testSimpleNativeTarget() throws Exception {
        runTest("testData/buildFileGeneration/simpleNativeTarget/");
    }

    @TestMetadata("singlePlatformJsBrowser")
    public void testSingleplatformJs() throws Exception {
        runTest("testData/buildFileGeneration/singlePlatformJsBrowser/");
    }
}
